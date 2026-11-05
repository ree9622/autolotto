package com.umicorp.autolotto.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umicorp.autolotto.AppContainer
import com.umicorp.autolotto.data.FallbackPolicy
import com.umicorp.autolotto.data.NumberConfig720
import com.umicorp.autolotto.data.Rank720
import com.umicorp.autolotto.data.RankChecker720
import com.umicorp.autolotto.data.Slot720
import com.umicorp.autolotto.data.Ticket720
import com.umicorp.autolotto.data.WinningNumbers720
import com.umicorp.autolotto.data.lumpPrize
import com.umicorp.autolotto.dhlottery.PensionOrder
import com.umicorp.autolotto.dhlottery.PensionOutcome
import com.umicorp.autolotto.dhlottery.PurchaseResult720
import com.umicorp.autolotto.dhlottery.Round720
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZonedDateTime

/** 연금복권 구매 다이얼로그 상태. 확인창에 보여 준 내용을 그대로 담아 표시와 결제가 어긋나지 않게 한다. */
sealed interface PensionBuyState {
    data object Idle : PensionBuyState
    data object NeedsSetup : PensionBuyState
    data class ConfirmingFirst(val round: Int, val config: NumberConfig720) : PensionBuyState
    data class PickingExtra(val round: Int) : PensionBuyState
    data object InProgress : PensionBuyState
    data class Success(val result: PurchaseResult720, val recordFailed: Boolean = false) : PensionBuyState
    data object AlreadyPurchased : PensionBuyState
    data object SaleClosed : PensionBuyState
    data object RoundChanged : PensionBuyState
    data object NothingBought : PensionBuyState
    data object NotLoggedIn : PensionBuyState
    data object ResultUnknown : PensionBuyState
    data class Failed(val message: String?) : PensionBuyState
}

/** 연금복권 내역 조회 기간. 동행복권 조회 한도(3개월) 안에서 최근 1년을 겹치지 않게 나눈다. */
internal fun pensionHistoryWindows(today: LocalDate): List<Pair<LocalDate, LocalDate>> = (0 until 4).map { i ->
    val start = today.minusMonths((i + 1) * 3L).coerceAtLeast(today.minusYears(1))
    val end = if (i == 0) today else today.minusMonths(i * 3L).minusDays(1)
    start to end
}

/** 내역 카드 한 장 분량. [ranks]는 [tickets]와 같은 순서다. */
internal data class PensionRoundGroup(
    val round: Int,
    val tickets: List<Ticket720>,
    val ranks: List<Rank720>,
    val winning: WinningNumbers720?,
) {
    val won: Boolean get() = ranks.any { it != Rank720.NONE && it != Rank720.PENDING }
    val lumpTotal: Long get() = ranks.sumOf { it.lumpPrize }
}

/**
 * 티켓을 최신 회차부터 묶는다. 당첨번호가 있으면 그것으로 다시 판정해 내역 응답의 미추첨 표시로 낙첨처럼 보이는 일을
 * 막고, 없으면 내역의 등수를 쓴다. 등수도 없으면 추첨 전으로 둔다.
 */
internal fun pensionRoundGroups(
    tickets: List<Ticket720>,
    winningByRound: Map<Int, WinningNumbers720>,
): List<PensionRoundGroup> = tickets.groupBy { it.round }.toSortedMap(compareByDescending { it }).map { (round, list) ->
    val winning = winningByRound[round]
    PensionRoundGroup(
        round = round,
        tickets = list,
        ranks = list.map { ticket ->
            winning?.let { RankChecker720.rankOf(ticket.jo, ticket.number, it) } ?: ticket.rank ?: Rank720.PENDING
        },
        winning = winning,
    )
}

/** 연금 탭: 당첨번호·번호 설정·구매·자동구매·내역. 공유 상태는 [AppContainer]가 단일 출처다. */
class PensionViewModel(private val container: AppContainer) : ViewModel() {
    val isLoggedIn = container.isLoggedIn
    val balance = container.balance
    val config = container.pensionConfig
    val configLoaded = container.pensionConfigLoaded
    val autoEnabled = container.pensionAutoEnabled
    val autoDay = container.pensionAutoDay
    val autoHour = container.pensionAutoHour
    val autoMinute = container.pensionAutoMinute
    val purchasedRound = container.pensionPurchasedRound

    val upcomingRound: Int get() = Round720.getUpcomingDrawRound()

    fun isSaleOpenNow(): Boolean = !Round720.isSalesClosed(ZonedDateTime.now(Round720.KST))

    // === 당첨번호 ===

    private val _winning = MutableStateFlow<WinningNumbers720?>(null)
    val winning: StateFlow<WinningNumbers720?> = _winning.asStateFlow()
    private val _winningLoading = MutableStateFlow(false)
    val winningLoading: StateFlow<Boolean> = _winningLoading.asStateFlow()

    fun loadWinning() {
        viewModelScope.launch {
            _winningLoading.value = true
            runCatching { container.resultService720.getWinningNumbers() }
                .onFailure { if (it is CancellationException) throw it }
                .getOrNull()?.let { _winning.value = it }
            _winningLoading.value = false
        }
    }

    // === 번호 설정 ===

    private val _saveFailed = MutableStateFlow(false)
    val saveFailed: StateFlow<Boolean> = _saveFailed.asStateFlow()

    fun consumeSaveFailed() { _saveFailed.value = false }

    fun saveConfig(slots: List<Slot720>, fallback: FallbackPolicy, setMode: Boolean) {
        if (!configLoaded.value) return   // 저장본을 읽기 전 빈 값으로 덮어쓰지 않는다.
        viewModelScope.launch {
            val ok = container.savePensionConfig(slots, fallback, setMode)
            if (!ok) _saveFailed.value = true
        }
    }

    // === 구매 ===

    private val _buyState = MutableStateFlow<PensionBuyState>(PensionBuyState.Idle)
    val buyState: StateFlow<PensionBuyState> = _buyState.asStateFlow()
    private var tapJob: Job? = null

    /** 구매 버튼: 첫 구매는 저장 설정 확인창, 이번 회차를 이미 샀으면 추가 구매 선택. */
    fun onBuyTap() {
        if (_buyState.value != PensionBuyState.Idle || tapJob?.isActive == true) return
        tapJob = viewModelScope.launch {
            if (!isSaleOpenNow()) {
                advanceFromIdle(PensionBuyState.SaleClosed)
                return@launch
            }
            runCatching { container.refreshPensionPurchasedRound() }
                .onFailure { if (it is CancellationException) throw it }
            val round = upcomingRound
            val saved = config.value
            when {
                purchasedRound.value >= round -> advanceFromIdle(PensionBuyState.PickingExtra(round))
                saved.gameCount == 0 -> advanceFromIdle(PensionBuyState.NeedsSetup)
                else -> advanceFromIdle(PensionBuyState.ConfirmingFirst(round, saved))
            }
        }
    }

    /** 이중 탭으로 뜬 늦은 코루틴이 진행 상태를 되돌려 재확정(이중 결제)하는 일을 막는다. */
    private fun advanceFromIdle(next: PensionBuyState) {
        if (_buyState.value == PensionBuyState.Idle) _buyState.value = next
    }

    fun confirmFirst() {
        val s = _buyState.value as? PensionBuyState.ConfirmingFirst ?: return
        launchBuy(PensionOrder.Configured(s.config), s.round)
    }

    fun confirmExtraAuto(games: Int) {
        val s = _buyState.value as? PensionBuyState.PickingExtra ?: return
        launchBuy(PensionOrder.ExtraAuto(games), s.round)
    }

    fun confirmExtraSet() {
        val s = _buyState.value as? PensionBuyState.PickingExtra ?: return
        launchBuy(PensionOrder.ExtraSet, s.round)
    }

    fun dismissBuy() {
        if (_buyState.value == PensionBuyState.InProgress) return   // 진행 중 닫기 금지
        _buyState.value = PensionBuyState.Idle
    }

    private fun launchBuy(order: PensionOrder, round: Int) {
        _buyState.value = PensionBuyState.InProgress
        viewModelScope.launch {
            _buyState.value = try {
                when (val outcome = container.buyPension(order, round)) {
                    is PensionOutcome.Purchased -> PensionBuyState.Success(outcome.result)
                    is PensionOutcome.RecordFailed -> PensionBuyState.Success(outcome.result, recordFailed = true)
                    PensionOutcome.AlreadyPurchased -> PensionBuyState.AlreadyPurchased
                    PensionOutcome.SaleClosed -> PensionBuyState.SaleClosed
                    PensionOutcome.RoundChanged -> PensionBuyState.RoundChanged
                    PensionOutcome.NothingBought -> PensionBuyState.NothingBought
                    PensionOutcome.NotLoggedIn -> PensionBuyState.NotLoggedIn
                    is PensionOutcome.ResultUnknown, PensionOutcome.PendingUnresolved -> PensionBuyState.ResultUnknown
                    is PensionOutcome.Rejected -> PensionBuyState.Failed(outcome.message)
                    is PensionOutcome.LoginFailed, PensionOutcome.PreparationFailed -> PensionBuyState.Failed(null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 관리자 밖의 예외는 결제 뒤일 수 있다. 재시도를 유도하지 않는다.
                PensionBuyState.ResultUnknown
            }
            if (_buyState.value is PensionBuyState.Success) loadHistory()
        }
    }

    // === 자동구매 ===

    fun setAutoEnabled(v: Boolean) { viewModelScope.launch { container.setPensionAutoEnabled(v) } }

    /** 판매 중지 시간에 걸리면 false — 그 주 구매를 놓치는 설정을 저장 단계에서 막는다. */
    fun setAutoDay(day: Int): Boolean {
        if (Round720.isScheduleBlocked(day, autoHour.value, autoMinute.value)) return false
        viewModelScope.launch { container.setPensionAutoDay(day) }
        return true
    }

    fun setAutoTime(hour: Int, minute: Int): Boolean {
        if (Round720.isScheduleBlocked(autoDay.value, hour, minute)) return false
        viewModelScope.launch { container.setPensionAutoTime(hour, minute) }
        return true
    }

    // === 내역 ===

    private val _tickets = MutableStateFlow<List<Ticket720>>(emptyList())
    val tickets: StateFlow<List<Ticket720>> = _tickets.asStateFlow()
    private val _winningByRound = MutableStateFlow<Map<Int, WinningNumbers720>>(emptyMap())
    val winningByRound: StateFlow<Map<Int, WinningNumbers720>> = _winningByRound.asStateFlow()
    private val _historyLoading = MutableStateFlow(false)
    val historyLoading: StateFlow<Boolean> = _historyLoading.asStateFlow()
    private val _historyFailed = MutableStateFlow(false)
    val historyFailed: StateFlow<Boolean> = _historyFailed.asStateFlow()
    private val _canLoadMore = MutableStateFlow(false)
    val canLoadMore: StateFlow<Boolean> = _canLoadMore.asStateFlow()

    private var shownUser: String? = null
    private var windowsLoaded = 0
    private var historyJob: Job? = null

    init {
        loadWinning()
        viewModelScope.launch {
            combine(container.isLoggedIn, container.loggedInUserId) { logged, user -> logged to user }
                .collect { (logged, user) ->
                    if (!logged) {
                        resetHistory()
                    } else if (shownUser != user || windowsLoaded == 0) {
                        resetHistory()
                        shownUser = user
                        loadHistory()
                    }
                }
        }
    }

    private fun resetHistory() {
        historyJob?.cancel()
        historyJob = null
        shownUser = null
        windowsLoaded = 0
        _tickets.value = emptyList()
        _winningByRound.value = emptyMap()
        _canLoadMore.value = false
        _historyFailed.value = false
        _historyLoading.value = false
    }

    /** 이미 불러온 기간 전체를 다시 조회한다. 실패하면 기존 목록을 그대로 둔다. */
    fun loadHistory() = runHistory(windowCount = windowsLoaded.coerceAtLeast(1))

    fun loadMoreHistory() = runHistory(windowCount = windowsLoaded + 1)

    private fun runHistory(windowCount: Int) {
        if (historyJob?.isActive == true || !container.isLoggedIn.value) return
        val user = shownUser
        val windows = pensionHistoryWindows(LocalDate.now(Round720.KST))
        val count = windowCount.coerceAtMost(windows.size)
        _historyLoading.value = true
        _historyFailed.value = false
        historyJob = viewModelScope.launch {
            try {
                val loaded = windows.take(count).flatMap { (from, to) ->
                    container.historyService720.fetchPurchases(from, to)
                }
                if (user != container.loggedInUserId.value) return@launch   // 이전 계정의 늦은 응답은 버린다.
                _tickets.value = loaded
                windowsLoaded = count
                _canLoadMore.value = count < windows.size
                _winningByRound.value = container.resultService720.getWinningNumbersMap(loaded.mapTo(mutableSetOf()) { it.round })
                container.reconcilePension(loaded, user)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _historyFailed.value = true
            } finally {
                _historyLoading.value = false
            }
        }
    }
}
