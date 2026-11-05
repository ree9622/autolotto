package com.umicorp.autolotto.dhlottery

import com.umicorp.autolotto.data.NumberConfig720
import com.umicorp.autolotto.data.Ticket720
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZonedDateTime

/** 연금복권 구매 상태 저장소. [com.umicorp.autolotto.data.SecureStore]가 구현하고 시험은 가짜로 대체한다. */
interface PensionStateStore {
    fun pensionUserId(): String?
    fun pensionPassword(): String?
    fun getPensionLastRound(): Int
    fun getPensionLastOwner(): String?
    fun setPensionLastPurchase(round: Int, owner: String): Boolean
    fun getPensionPending(): String?
    fun setPensionPending(json: String): Boolean
    fun clearPensionPending(): Boolean
    fun getPensionLedger(): String?
    fun setPensionLedger(json: String): Boolean
}

/** 동행복권 쪽 호출. 구매마다 전용 세션을 쓰는 구현은 [DhlotteryPensionGateway]다. */
interface PensionGateway {
    suspend fun login(userId: String, password: String)
    suspend fun purchase(order: PensionOrder, round: Int): PurchaseResult720
    /** 실패·세션 만료는 던진다. 빈 목록은 정말 구매가 없다는 뜻이다. */
    suspend fun recentTickets(): List<Ticket720>
}

/** 무엇을 살지. 첫 구매는 저장한 설정, 추가 구매는 저장 번호와 겹치지 않게 새 자동 번호만 산다. */
sealed interface PensionOrder {
    val amount: Int

    data class Configured(val config: NumberConfig720) : PensionOrder {
        override val amount: Int get() = config.gameCount * UNIT_PRICE
    }

    data class ExtraAuto(val games: Int) : PensionOrder {
        init { require(games in 1..5) { "게임 수는 1~5개여야 합니다: $games" } }
        override val amount: Int get() = games * UNIT_PRICE
    }

    data object ExtraSet : PensionOrder {
        override val amount: Int get() = SET_GAMES * UNIT_PRICE
    }

    companion object {
        const val UNIT_PRICE = 1000
        const val SET_GAMES = 5
    }
}

/** 구매 시도의 결과. 호출자(화면·워커)는 이 값을 문구로만 옮기고 안전 판단은 하지 않는다. */
sealed interface PensionOutcome {
    /** 결제 응답을 확인했거나 내역 대조로 확정된 구매. 부분 성공이면 [PurchaseResult720.partialFailure]가 있다. */
    data class Purchased(val result: PurchaseResult720) : PensionOutcome
    /** 결제는 됐지만 재구매 방지 기록을 저장하지 못했다. 호출자는 자동구매를 꺼야 한다. */
    data class RecordFailed(val result: PurchaseResult720) : PensionOutcome
    /** 지정 번호가 모두 팔려 '구매 포기' 정책으로 결제 없이 끝났다. */
    data object NothingBought : PensionOutcome
    /** 이 계정이 이번 회차를 이미 샀다(첫 구매만 해당). */
    data object AlreadyPurchased : PensionOutcome
    data object SaleClosed : PensionOutcome
    /** 확인창에 보여 준 회차와 실제 회차가 달라 결제 없이 멈췄다. */
    data object RoundChanged : PensionOutcome
    data object NotLoggedIn : PensionOutcome
    /** 로그인 실패 — 결제 전이라 다시 시도해도 안전하다. */
    data class LoginFailed(val cause: Exception) : PensionOutcome
    /** 결제 준비 기록 실패 — 결제 전이라 다시 시도해도 안전하다. */
    data object PreparationFailed : PensionOutcome
    /** 서버가 거절했거나 결제 전 단계에서 실패했다. 결제되지 않았으니 다시 시도해도 안전하다. */
    data class Rejected(val message: String?) : PensionOutcome
    /** 결제 요청의 결과를 알 수 없다. 이중결제를 막기 위해 다시 시도하면 안 된다. */
    data class ResultUnknown(val cause: Throwable) : PensionOutcome
    /** 앞선 결과 불명 구매가 아직 확인되지 않아 이번 회차 결제를 막았다. */
    data object PendingUnresolved : PensionOutcome
}

/**
 * 연금복권 구매의 금전 안전 경로. 즉시 구매와 예약 구매가 같은 절차를 지나도록 한곳에 모았다.
 *
 * connPro는 멱등이 아니다. 그래서 다음을 지킨다.
 * - 결제 전에 미결 기록(PENDING)을 남기고, 프로세스가 죽어도 그 회차 재결제를 막는다.
 * - 결과 불명이면 재시도하지 않고 회차 가드를 세운 뒤 구매 내역으로 대조한다. 대조로 금액이 전부
 *   설명될 때만 미결을 푼다(티켓 존재만으로 풀면 "첫 구매 성공 + 추가 구매 불명"에서 이중결제가 열린다).
 * - 미결 해제 전에 가드·원장을 먼저 저장한다. 하나라도 실패하면 미결을 남겨 마지막 가드로 쓴다.
 * - 계정 전환 뒤에는 다른 계정의 내역으로 미결을 풀지 않는다.
 *
 * [lock]은 6/45와 공유하는 PurchaseLock이다. 임계구역 안에서 loginMutex를 잡지 않아(전용 세션)
 * 기존 락 순서를 건드리지 않는다.
 */
class PensionPurchaseManager(
    private val store: PensionStateStore,
    private val lock: Mutex,
    private val now: () -> ZonedDateTime = { ZonedDateTime.now(Round720.KST) },
) {

    /** [expectedRound]는 확인창이 보여 준 회차. 예약 구매는 null(그 시점 회차를 산다). */
    suspend fun purchase(gateway: PensionGateway, order: PensionOrder, expectedRound: Int?): PensionOutcome = lock.withLock {
        val current = now()
        if (Round720.isSalesClosed(current)) return@withLock PensionOutcome.SaleClosed
        val round = Round720.getUpcomingDrawRound(current)
        if (expectedRound != null && expectedRound != round) return@withLock PensionOutcome.RoundChanged

        val userId = store.pensionUserId()
        val password = store.pensionPassword()
        if (userId == null || password == null) return@withLock PensionOutcome.NotLoggedIn

        val recorded = accountScopedRound(store.getPensionLastRound(), store.getPensionLastOwner(), userId)
        if (order is PensionOrder.Configured && recorded >= round) return@withLock PensionOutcome.AlreadyPurchased

        try {
            gateway.login(userId, password)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withLock PensionOutcome.LoginFailed(e)
        }

        // 같은 회차에 미결이 남았으면 어떤 결제도 하지 않는다. 내역으로 앞선 결제가 확인되면 결과만 돌려준다 —
        // 사용자는 앞선 시도가 실패한 줄 알고 눌렀으므로 여기서 결제를 이어가면 이중결제가 된다.
        val pendingRaw = store.getPensionPending()
        val pending = parsePendingAttempt(pendingRaw)
        if (pending != null && pending.round == round) {
            val settled = (if (pending.owner == userId) settle(pending, userId, ticketsOrEmpty(gateway)) else null)
                ?: return@withLock PensionOutcome.PendingUnresolved
            return@withLock PensionOutcome.Purchased(
                PurchaseResult720(round, settled, pending.amount, confirmedByHistory = true),
            )
        }

        // connPro 앞에서 먼저 저장한다. 저장 실패면 결제에 들어가지 않는다(fail-closed).
        val attempt = PendingAttempt(round, today(), order.amount, inLedger = false, owner = userId)
        if (!store.setPensionPending(attempt.toJson())) return@withLock PensionOutcome.PreparationFailed
        val spentBefore = spentInRound(round)

        val result = try {
            gateway.purchase(order, round)
        } catch (e: CancellationException) {
            throw e // 미결 유지: 요청이 이미 나갔을 수 있다.
        } catch (e: Exception) {
            return@withLock when (classifyPensionFailure(e)) {
                is PensionFailure.Rejected -> {
                    runCatching { store.clearPensionPending() }
                    PensionOutcome.Rejected(e.message)
                }
                is PensionFailure.Unknown -> reconcileUnknown(gateway, attempt, spentBefore, e)
            }
        }

        if (result.tickets.isEmpty()) {
            // 한 장도 결제되지 않았다. 가드를 세우면 번호를 바꿔 다시 사는 길까지 막힌다.
            runCatching { store.clearPensionPending() }
            return@withLock PensionOutcome.NothingBought
        }
        commitSuccess(attempt, result)
    }

    /**
     * 내역 탭에서 조회한 목록으로 미결을 해소한다. 안내 문구의 "내역에서 확인"이 실제로 동작하게 하는 경로다.
     * 조회와 락 획득 사이에 계정이 바뀔 수 있어 [ticketsOwner]를 락 안에서 다시 확인한다.
     */
    suspend fun reconcile(tickets: List<Ticket720>, ticketsOwner: String?): Boolean = lock.withLock {
        val pending = parsePendingAttempt(store.getPensionPending()) ?: return@withLock false
        val userId = store.pensionUserId()
        if (userId == null || ticketsOwner != userId || pending.owner != userId) return@withLock false
        settle(pending, userId, tickets) != null
    }

    /** 이 계정 기준으로 해당 회차를 이미 샀는가. */
    fun purchasedRound(userId: String?): Int =
        accountScopedRound(store.getPensionLastRound(), store.getPensionLastOwner(), userId)

    private suspend fun reconcileUnknown(
        gateway: PensionGateway,
        attempt: PendingAttempt,
        spentBefore: Int,
        cause: Exception,
    ): PensionOutcome {
        // 커밋을 조회보다 먼저 끝낸다. 조회 왕복 중 프로세스가 죽어도 가드가 남는다.
        val owner = requireNotNull(attempt.owner)
        val (guardOk, ledgerOk) = withContext(NonCancellable) {
            val guard = runCatching { store.setPensionLastPurchase(attempt.round, owner) }.getOrDefault(false)
            val ledger = runCatching { recordSpend(attempt.round, attempt.amount) }.getOrDefault(false)
            if (ledger) runCatching { store.setPensionPending(attempt.copy(inLedger = true).toJson()) }
            guard to ledger
        }
        val seen = ticketsOrEmpty(gateway)
        if (!isRoundCoveredByHistory(attempt.round, spentBefore + attempt.amount, seen)) {
            return PensionOutcome.ResultUnknown(cause)
        }
        val confirmed = PurchaseResult720(
            attempt.round,
            seen.filter { it.round == attempt.round },
            attempt.amount,
            confirmedByHistory = true,
        )
        return withContext(NonCancellable) {
            if (guardOk && ledgerOk && runCatching { store.clearPensionPending() }.getOrDefault(false)) {
                PensionOutcome.Purchased(confirmed)
            } else {
                PensionOutcome.RecordFailed(confirmed)
            }
        }
    }

    private suspend fun commitSuccess(attempt: PendingAttempt, result: PurchaseResult720): PensionOutcome =
        withContext(NonCancellable) {
            val owner = requireNotNull(attempt.owner)
            val guardOk = runCatching { store.setPensionLastPurchase(attempt.round, owner) }.getOrDefault(false)
            // 결과 불명이 섞인 부분 성공은 시도 전액을 기록한다. 실제보다 크게 잡혀야 다음 대조가 안전 측으로 닫힌다.
            val failure = result.partialFailure
            val hasUnknown = failure != null && classifyPensionFailure(failure.cause) is PensionFailure.Unknown
            val spent = if (hasUnknown) attempt.amount else result.amount
            val ledgerOk = runCatching { recordSpend(attempt.round, spent) }.getOrDefault(false)
            if (!guardOk || !ledgerOk) return@withContext PensionOutcome.RecordFailed(result)
            // 미확정 게임이 남은 부분 성공은 미결을 유지한다. 나머지는 해제해도 안전하다.
            if (hasUnknown) {
                runCatching { store.setPensionPending(attempt.copy(inLedger = true).toJson()) }
            } else {
                runCatching { store.clearPensionPending() }
            }
            PensionOutcome.Purchased(result)
        }

    /** 내역이 앞선 시도를 전부 설명하면 가드·원장을 다시 저장하고 미결을 푼다. 하나라도 실패하면 null. */
    private suspend fun settle(pending: PendingAttempt, userId: String, tickets: List<Ticket720>): List<Ticket720>? =
        withContext(NonCancellable) {
            val expected = if (pending.inLedger) spentInRound(pending.round) else spentInRound(pending.round) + pending.amount
            if (!isRoundCoveredByHistory(pending.round, expected, tickets)) return@withContext null
            // 지난 회차 미결을 풀 때 더 최근 회차 가드를 낮추면 재구매가 열린다. 항상 큰 값을 남긴다.
            val guardRound = maxOf(pending.round, purchasedRound(userId))
            if (!runCatching { store.setPensionLastPurchase(guardRound, userId) }.getOrDefault(false)) return@withContext null
            if (!pending.inLedger) {
                if (!runCatching { recordSpend(pending.round, pending.amount) }.getOrDefault(false)) return@withContext null
                // 원장 반영을 바로 새긴다. 이게 없으면 아래 해제가 실패했을 때 다음 판정이 금액을 두 번 더한다.
                runCatching { store.setPensionPending(pending.copy(inLedger = true).toJson()) }
            }
            if (!runCatching { store.clearPensionPending() }.getOrDefault(false)) return@withContext null
            tickets.filter { it.round == pending.round }
        }

    /** 조회 실패는 미확인으로 처리한다. 대조 판정이 금액 0으로 닫혀 미결이 유지된다. */
    private suspend fun ticketsOrEmpty(gateway: PensionGateway): List<Ticket720> = try {
        gateway.recentTickets()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }

    private fun today(): Long = now().withZoneSameInstant(Round720.KST).toLocalDate().toEpochDay()

    private fun spentInRound(round: Int): Int =
        parseSpendLedger(store.getPensionLedger()).filter { it.round == round }.sumOf { it.amount }

    private fun recordSpend(round: Int, amount: Int): Boolean {
        val day = today()
        val next = (parseSpendLedger(store.getPensionLedger()) + SpendEntry(round, day, amount))
            .filter { it.epochDay > day - LEDGER_KEEP_DAYS }
        return store.setPensionLedger(spendLedgerToJson(next))
    }

    private companion object {
        /** 한 회차는 7일을 넘지 않지만 경계에서 잘리지 않도록 넉넉히 둔다. */
        const val LEDGER_KEEP_DAYS = 14
    }
}

/** 실제 동행복권 연결. 구매마다 전용 세션을 만들어 화면의 공유 세션 변경과 섞이지 않게 한다. */
class DhlotteryPensionGateway(
    private val resultService: ResultService720,
    private val session: DhlotterySession = DhlotterySession(),
) : PensionGateway {
    private val auth = AuthService(session)

    override suspend fun login(userId: String, password: String) {
        auth.login(userId, password)
    }

    override suspend fun purchase(order: PensionOrder, round: Int): PurchaseResult720 {
        val service = PurchaseService720(session)
        return when (order) {
            is PensionOrder.Configured -> service.purchase(order.config, round)
            is PensionOrder.ExtraAuto -> service.purchase(order.games, round)
            PensionOrder.ExtraSet -> service.purchase(NumberConfig720.empty().copy(setMode = true), round)
        }
    }

    override suspend fun recentTickets(): List<Ticket720> =
        HistoryService720(session, resultService).fetchRecentPurchases()

    /** 구매 후 잔액. 로그인 세션을 재사용하며 실패는 호출자가 무시한다. */
    suspend fun balance(): Int = auth.getBalance()
}

/** 구매 실패 분류 — [Rejected]=결제 안 됨(재시도 안전), [Unknown]=결과 불명(재시도 금지). */
sealed interface PensionFailure {
    val cause: Throwable
    data class Rejected(override val cause: Throwable) : PensionFailure
    data class Unknown(override val cause: Throwable) : PensionFailure
}

/**
 * [PurchaseService720]는 결과 불명에 "결과 불명" 표식을 넣어 던진다. 표식이 없는 DhlotteryException만
 * 결제 안 됨으로 본다. 그 외 예외는 connPro 뒤일 수 있으므로 전부 결과 불명이다.
 */
fun classifyPensionFailure(e: Throwable): PensionFailure = when {
    e is DhlotteryException && e.message?.contains("결과 불명") != true -> PensionFailure.Rejected(e)
    else -> PensionFailure.Unknown(e)
}

/**
 * 계정별 회차 가드 — 기록 소유자가 현재 계정과 다르면 이 계정은 미구매(0)다.
 * 로그인할 때 지우지 않고 판정할 때 대조해 A→B→A 전환에서 A의 가드가 살아남게 한다.
 */
fun accountScopedRound(recordedRound: Int, recordedOwner: String?, currentUserId: String?): Int =
    if (currentUserId != null && recordedOwner == currentUserId) recordedRound else 0

/**
 * 서버 내역이 이 회차에 청구로 계산한 금액을 전부 설명하는가. 조회 실패는 빈 목록이라 false로 닫힌다.
 * 금액 대조라 앱 밖(웹·다른 기기)에서 같은 회차를 샀다면 실패한 시도가 확정될 수 있다. 다만 그 방향은
 * 회차 가드를 세울 뿐이라 이중결제로 이어지지 않는다.
 */
fun isRoundCoveredByHistory(round: Int, expectedAmount: Int, tickets: List<Ticket720>): Boolean {
    if (expectedAmount <= 0) return false
    return tickets.count { it.round == round } * PensionOrder.UNIT_PRICE >= expectedAmount
}

/** 결제 전에 남기는 미결 기록. [inLedger]=이 시도 금액이 이미 원장에 들어갔는가. */
data class PendingAttempt(
    val round: Int,
    val epochDay: Long,
    val amount: Int,
    val inLedger: Boolean,
    val owner: String?,
) {
    fun toJson(): String = JSONObject()
        .put("round", round)
        .put("epochDay", epochDay)
        .put("amount", amount)
        .put("inLedger", inLedger)
        .apply { if (owner != null) put("owner", owner) }
        .toString()
}

/** 손상됐거나 필수 값이 없으면 null. 호출자는 null을 "미결 없음"으로 처리한다. */
fun parsePendingAttempt(json: String?): PendingAttempt? {
    if (json.isNullOrBlank()) return null
    val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
    if (!o.has("round")) return null
    val amount = o.optInt("amount", -1)
    val epochDay = o.optLong("epochDay", -1)
    if (amount < 0 || epochDay < 0) return null
    return PendingAttempt(
        round = o.optInt("round"),
        epochDay = epochDay,
        amount = amount,
        inLedger = o.optBoolean("inLedger", false),
        owner = o.optString("owner").takeIf { it.isNotBlank() },
    )
}

/** 이 앱이 청구한 금액 원장 1건. 결과 불명 대조의 기대 금액을 계산하는 데만 쓴다. */
data class SpendEntry(val round: Int, val epochDay: Long, val amount: Int)

fun parseSpendLedger(json: String?): List<SpendEntry> {
    if (json.isNullOrBlank()) return emptyList()
    val arr = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        arr.optJSONObject(i)?.let {
            val amount = it.optInt("amount", -1)
            if (amount < 0) null else SpendEntry(it.optInt("round"), it.optLong("epochDay"), amount)
        }
    }
}

fun spendLedgerToJson(entries: List<SpendEntry>): String = JSONArray().apply {
    entries.forEach { put(JSONObject().put("round", it.round).put("epochDay", it.epochDay).put("amount", it.amount)) }
}.toString()
