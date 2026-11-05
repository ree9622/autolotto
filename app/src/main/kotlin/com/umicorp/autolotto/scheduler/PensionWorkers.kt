package com.umicorp.autolotto.scheduler

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.umicorp.autolotto.data.NumberConfig720
import com.umicorp.autolotto.data.Rank720
import com.umicorp.autolotto.data.RankChecker720
import com.umicorp.autolotto.data.SecureStore
import com.umicorp.autolotto.data.Ticket720
import com.umicorp.autolotto.data.WinningNumbers720
import com.umicorp.autolotto.data.lumpPrize
import com.umicorp.autolotto.dhlottery.AuthService
import com.umicorp.autolotto.dhlottery.DhlotteryPensionGateway
import com.umicorp.autolotto.dhlottery.DhlotterySession
import com.umicorp.autolotto.dhlottery.HistoryService720
import com.umicorp.autolotto.dhlottery.PensionFailure
import com.umicorp.autolotto.dhlottery.PensionOrder
import com.umicorp.autolotto.dhlottery.PensionOutcome
import com.umicorp.autolotto.dhlottery.PensionPurchaseManager
import com.umicorp.autolotto.dhlottery.PurchaseResult720
import com.umicorp.autolotto.dhlottery.ResultService720
import com.umicorp.autolotto.dhlottery.Round720
import com.umicorp.autolotto.dhlottery.classifyPensionFailure
import kotlinx.coroutines.CancellationException

/**
 * 연금복권 자동구매 백그라운드 작업. 안전 판단은 [PensionPurchaseManager]가 하고,
 * 여기서는 결과를 알림으로 옮긴 뒤 다음 주 알람을 다시 건다. 결제 전 실패만 재시도한다.
 */
class PensionAutoPurchaseWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val ctx = applicationContext

    // lazy: 생성(Keystore 접근) 실패가 doWork try 안에서 잡혀 재시도로 흡수되게 한다.
    private val store by lazy { SecureStore(ctx) }

    override suspend fun doWork(): Result {
        var result = Result.success()
        try {
            if (shouldRetry(execute()) && runAttemptCount < MAX_ATTEMPTS - 1) result = Result.retry()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 저장소 읽기 실패 등 결제 이전 오류. 결제 이후 경로는 execute 안에서 결과로 돌아온다.
            if (runAttemptCount < MAX_ATTEMPTS - 1) {
                result = Result.retry()
            } else {
                notifySafely(PensionNotice("⚠️ 연금복권 자동 구매 실패", "앱을 열어 상태를 확인해주세요."))
            }
        }

        // 다음 주 알람 재등록(one-shot 체인). 멱등이라 재시도 회차에 또 호출돼도 안전하다.
        try {
            if (store.getPensionAutoEnabled()) AlarmScheduler(ctx).schedulePensionAutoPurchase()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            notifySafely(PensionNotice("⚠️ 연금복권 오류", "알람 재등록에 실패했습니다."))
        }
        return result
    }

    /** @return 이번 실행의 결과. 구매를 시도할 조건이 아니면 null. */
    private suspend fun execute(): PensionOutcome? {
        if (!store.getPensionAutoEnabled()) return null
        val config = NumberConfig720.fromJson(store.getPensionNumberConfig()) ?: return null
        if (config.gameCount == 0) return null

        val gateway = DhlotteryPensionGateway(ResultService720())
        val manager = PensionPurchaseManager(store, PurchaseLock.mutex)
        val outcome = manager.purchase(gateway, PensionOrder.Configured(config), expectedRound = null)

        if (outcome is PensionOutcome.RecordFailed) {
            // 재구매 방지 기록이 없으니 다음 실행이 같은 회차를 또 사지 않도록 자동구매를 끈다.
            store.setPensionAutoEnabled(false)
            AlarmScheduler(ctx).cancelPension()
        }
        if (outcome is PensionOutcome.Purchased || outcome is PensionOutcome.RecordFailed) {
            runCatching { BalanceAlert.checkAndNotify(ctx, gateway.balance()) }
                .onFailure { if (it is CancellationException) throw it }
        }
        val isLastAttempt = runAttemptCount >= MAX_ATTEMPTS - 1
        pensionAutoNotice(outcome, isLastAttempt)?.let { notifySafely(it) }
        return outcome
    }

    private fun notifySafely(notice: PensionNotice) {
        runCatching { Notifications.show(ctx, notice.title, notice.body, NOTIFICATION_ID, tab = Notifications.TAB_PENSION) }
    }

    private companion object {
        /** 총 시도 횟수(최초 1 + 재시도 2). 간격은 SchedulerReceivers의 backoff(15분 선형). */
        const val MAX_ATTEMPTS = 3
        const val NOTIFICATION_ID = 11
    }
}

/**
 * 연금복권 결과확인 백그라운드 작업. 목 21:00에 방금 추첨된 회차를 내 티켓과 대조해 알린다.
 * 결과 미게시·조회 실패는 재시도하고, 시도를 다 쓰면 낙첨으로 단정하지 않고 당첨번호만 알린다.
 */
class PensionCheckResultWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val ctx = applicationContext
    private val store by lazy { SecureStore(ctx) }

    override suspend fun doWork(): Result {
        val lastAttempt = runAttemptCount >= MAX_ATTEMPTS - 1
        val done = try {
            execute(lastAttempt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (lastAttempt) notifySafely(PensionNotice("⚠️ 연금복권 오류", "당첨 결과 확인에 실패했습니다."))
            lastAttempt
        }

        try {
            if (store.getPensionAutoEnabled()) AlarmScheduler(ctx).schedulePensionCheckResult()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            notifySafely(PensionNotice("⚠️ 연금복권 오류", "결과확인 알람 재등록에 실패했습니다."))
        }
        return if (done) Result.success() else Result.retry()
    }

    /** @return true=완결, false=재시도 필요. */
    private suspend fun execute(lastAttempt: Boolean): Boolean {
        if (!store.getPensionAutoEnabled()) return true

        val round = Round720.getLatestCompletedRound()
        val winning = ResultService720().getWinningNumbers(round)
        if (winning == null) {
            if (!lastAttempt) return false
            notifySafely(PensionNotice("🎱 제 ${round}회 연금복권", "당첨번호를 아직 확인하지 못했습니다. 앱에서 다시 확인해주세요."))
            return true
        }

        val tickets = try {
            val userId = store.getCredentials().userId ?: throw IllegalStateException("no_credentials")
            val password = store.getCredentials().password ?: throw IllegalStateException("no_credentials")
            val session = DhlotterySession()
            AuthService(session).login(userId, password)
            HistoryService720(session, ResultService720(session)).fetchRecentPurchases()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!lastAttempt) return false
            // 내역을 못 봤을 뿐이다. 낙첨으로 알리지 않고 당첨번호만 보낸다.
            notifySafely(pensionWinningOnlyNotice(round, winning))
            return true
        }
        notifySafely(pensionResultNotice(round, winning, tickets))
        return true
    }

    private fun notifySafely(notice: PensionNotice) {
        runCatching { Notifications.show(ctx, notice.title, notice.body, NOTIFICATION_ID, tab = Notifications.TAB_PENSION) }
    }

    private companion object {
        /** 최초 1 + 재시도 3 — 목 21:00 기준 15분 선형 backoff로 ~22:30까지 커버. */
        const val MAX_ATTEMPTS = 4
        const val NOTIFICATION_ID = 12
    }
}

/** 알림 문구 한 건. 백그라운드 알림은 기존 6/45 알림처럼 한국어로 고정한다. */
internal data class PensionNotice(val title: String, val body: String)

/** 결제 전 실패만 재시도한다. 결제 이후 결과는 어떤 경우에도 다시 시도하지 않는다. */
private fun shouldRetry(outcome: PensionOutcome?): Boolean =
    outcome is PensionOutcome.LoginFailed || outcome is PensionOutcome.PreparationFailed

/** 예약 구매 결과 → 알림. 조용히 끝나도 되는 경우는 null. */
internal fun pensionAutoNotice(outcome: PensionOutcome?, isLastAttempt: Boolean): PensionNotice? = when (outcome) {
    null, PensionOutcome.AlreadyPurchased, PensionOutcome.NotLoggedIn, PensionOutcome.RoundChanged -> null
    is PensionOutcome.Purchased -> purchasedNotice(outcome.result)
    is PensionOutcome.RecordFailed -> PensionNotice(
        "⚠️ 연금복권 구매 기록 저장 실패",
        "제 ${outcome.result.round}회 구매는 완료됐지만 기록을 저장하지 못해 자동 구매를 껐습니다. 내역을 확인한 뒤 다시 켜주세요.",
    )
    PensionOutcome.NothingBought -> PensionNotice(
        "ℹ️ 연금복권 이번 회차 미구매",
        "지정한 번호가 이미 판매되어 '구매 포기' 설정에 따라 구매하지 않았습니다.",
    )
    PensionOutcome.SaleClosed -> PensionNotice(
        "⚠️ 연금복권 자동 구매 건너뜀",
        "판매 중지 시간(목 17:00~22:00)에 실행되어 이번 회차를 구매하지 못했습니다. 구매 시각을 확인해주세요.",
    )
    is PensionOutcome.LoginFailed -> if (isLastAttempt) PensionNotice(
        "⚠️ 연금복권 자동 구매 실패",
        "로그인에 실패했습니다. 아이디와 비밀번호를 확인해주세요.",
    ) else null
    PensionOutcome.PreparationFailed -> if (isLastAttempt) PensionNotice(
        "⚠️ 연금복권 자동 구매 실패",
        "구매를 준비하지 못했습니다. 결제되지 않았습니다. 앱을 열어 다시 시도해주세요.",
    ) else null
    is PensionOutcome.Rejected -> PensionNotice(
        "⚠️ 연금복권 자동 구매 실패",
        outcome.message ?: "구매되지 않았습니다. 앱을 열어 상태를 확인해주세요.",
    )
    is PensionOutcome.ResultUnknown, PensionOutcome.PendingUnresolved -> PensionNotice(
        "⚠️ 연금복권 구매 결과 확인 필요",
        "구매가 처리됐는지 확인하지 못했습니다. 이중 결제를 막기 위해 다시 시도하지 않았습니다. 연금 탭 내역을 확인해주세요.",
    )
}

private fun purchasedNotice(result: PurchaseResult720): PensionNotice {
    val lines = result.tickets.joinToString("\n") { "${it.jo}조 ${it.number}" }
    val partial = result.partialFailure?.let { failure ->
        if (classifyPensionFailure(failure.cause) is PensionFailure.Unknown) {
            "\n⚠️ 나머지 ${failure.failedGames}게임은 결과를 확인하지 못했습니다. 내역을 확인해주세요."
        } else {
            "\n⚠️ 나머지 ${failure.failedGames}게임은 구매되지 않았습니다."
        }
    }.orEmpty()
    return PensionNotice(
        "🎰 연금복권 자동 구매 완료!",
        "제 ${result.round}회 · ${result.tickets.size}게임\n$lines$partial",
    )
}

/** 내역을 확인하지 못했거나 이번 회차 구매가 없을 때의 당첨번호 알림. */
internal fun pensionWinningOnlyNotice(round: Int, winning: WinningNumbers720) = PensionNotice(
    "🎱 제 ${round}회 연금복권 당첨번호",
    "${winning.jo}조 ${winning.number} · 보너스 각 조 ${winning.bonusNumber}",
)

/** 이번 회차 티켓 대조 알림. 미추첨으로 남은 티켓도 당첨번호로 다시 판정해 낙첨 오보를 막는다. */
internal fun pensionResultNotice(round: Int, winning: WinningNumbers720, allTickets: List<Ticket720>): PensionNotice {
    val mine = allTickets.filter { it.round == round }
    if (mine.isEmpty()) return pensionWinningOnlyNotice(round, winning)
    val ranks = mine.map { RankChecker720.rankOf(it.jo, it.number, winning) }
    val lines = mine.zip(ranks).joinToString("\n") { (ticket, rank) ->
        val prize = pensionPrizeText(rank)
        "${ticket.jo}조 ${ticket.number} → ${pensionRankLabel(rank)}" + if (prize.isEmpty()) "" else " ($prize)"
    }
    val won = ranks.any { it != Rank720.NONE }
    val lump = ranks.sumOf { it.lumpPrize }
    val total = if (lump > 0) "\n\n일시금 합계: ₩${Notifications.formatThousands(lump)}" else ""
    return PensionNotice(
        if (won) "🎉 제 ${round}회 연금복권 당첨!" else "😔 제 ${round}회 연금복권 낙첨",
        "당첨번호: ${winning.jo}조 ${winning.number} · 보너스 각 조 ${winning.bonusNumber}\n\n$lines$total",
    )
}

internal fun pensionRankLabel(rank: Rank720): String = when (rank) {
    Rank720.FIRST -> "1등"
    Rank720.SECOND -> "2등"
    Rank720.THIRD -> "3등"
    Rank720.FOURTH -> "4등"
    Rank720.FIFTH -> "5등"
    Rank720.SIXTH -> "6등"
    Rank720.SEVENTH -> "7등"
    Rank720.BONUS -> "보너스"
    Rank720.NONE -> "낙첨"
    Rank720.PENDING -> "추첨 전"
}

/** 공식 당첨금표: 1등 월 700만×20년, 2등·보너스 월 100만×10년, 3~7등 일시금. */
internal fun pensionPrizeText(rank: Rank720): String = when (rank) {
    Rank720.FIRST -> "월 700만원×20년"
    Rank720.SECOND, Rank720.BONUS -> "월 100만원×10년"
    Rank720.NONE, Rank720.PENDING -> ""
    else -> "₩${Notifications.formatThousands(rank.lumpPrize)}"
}
