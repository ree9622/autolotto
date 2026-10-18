package com.umicorp.autolotto.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umicorp.autolotto.AppContainer
import com.umicorp.autolotto.data.Purchase
import com.umicorp.autolotto.data.WinningResult
import com.umicorp.autolotto.dhlottery.PurchaseResult
import com.umicorp.autolotto.dhlottery.PurchaseService
import com.umicorp.autolotto.TapSlotsOutcome
import com.umicorp.autolotto.splitSlots
import com.umicorp.autolotto.tapSlotsOutcome
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 화면별 ViewModel (원본 Riverpod 화면 상태 포트).
 *
 * 공유 반응형 상태(로그인·잔액·자동구매설정·언어·잔액알림)는 [AppContainer]가 단일 출처로 들고
 * 있고(원본 전역 프로바이더와 1:1), ViewModel은 그 StateFlow를 그대로 재노출 + 화면-로컬 상태와
 * 액션만 갖는다. suspend 서비스 호출은 전부 viewModelScope에서 수행한다.
 *
 * UI는 단위테스트 불가 → 검증은 컴파일 + assembleDebug. 실제 화면 배선은 Slice 5b.
 */

/** 홈: 카운트다운(회차) + 지난 회차 당첨번호 + 잔액/자동구매 상태(공유). */
class HomeViewModel(private val container: AppContainer) : ViewModel() {
    val isLoggedIn = container.isLoggedIn
    val autoLoginFailed = container.autoLoginFailed
    val hydrationFailed = container.hydrationFailed
    val balance = container.balance

    /** amber 클라우드 아이콘 탭 — 설정 하이드레이션·자동 로그인 수동 재시도(공용 복구 경로). */
    fun retryConnect() {
        viewModelScope.launch {
            container.ensureHydrated()
            container.autoLogin()
        }
    }
    val autoEnabled = container.autoEnabled
    val autoPurchaseDay = container.autoPurchaseDay
    val autoPurchaseHour = container.autoPurchaseHour
    val autoPurchaseMinute = container.autoPurchaseMinute
    val balanceAlertEnabled = container.balanceAlertEnabled
    val balanceAlertThreshold = container.balanceAlertThreshold

    /** 현재 판매 중인 회차(KST). 카운트다운 헤더용. */
    val currentRound: Int get() = PurchaseService.getCurrentRound()

    private val _winning = MutableStateFlow<WinningResult?>(null)
    val winning: StateFlow<WinningResult?> = _winning.asStateFlow()
    private val _loadingNumbers = MutableStateFlow(false)
    val loadingNumbers: StateFlow<Boolean> = _loadingNumbers.asStateFlow()

    init { fetchWinningNumbers() }

    /** 지난 회차(현재-1) 당첨번호 조회. 실패는 무시(원본 debugPrint 후 무시). */
    fun fetchWinningNumbers() {
        viewModelScope.launch {
            _loadingNumbers.value = true
            runCatching { container.resultService.getWinningNumbers(currentRound - 1) }
                .getOrNull()?.let { _winning.value = it }
            _loadingNumbers.value = false
        }
    }

    /** 당겨서 새로고침: 당첨번호 + 잔액. */
    fun refreshAll() {
        fetchWinningNumbers()
        viewModelScope.launch { container.refreshBalance() }
    }
}

/** 번호 설정: 5슬롯(null=미설정 / emptyList=자동 / [nums]=수동) 읽기·쓰기. */
class NumberViewModel(private val container: AppContainer) : ViewModel() {
    val autoEnabled = container.autoEnabled
    // 저장 완료 스낵바의 스케줄 문구용(원본 number_screen이 autoPurchaseDay/Hour/Minute 프로바이더를 읽음).
    val autoPurchaseDay = container.autoPurchaseDay
    val autoPurchaseHour = container.autoPurchaseHour
    val autoPurchaseMinute = container.autoPurchaseMinute

    private val _games = MutableStateFlow<List<List<Int>?>>(List(5) { null })
    val games: StateFlow<List<List<Int>?>> = _games.asStateFlow()

    /** 최초 로드 성공 전 저장 차단용 — 빈 초기 슬롯을 실데이터 위에 덮어쓰는 유실 방지(crosscheck R2). */
    private val _gamesLoaded = MutableStateFlow(false)
    val gamesLoaded: StateFlow<Boolean> = _gamesLoaded.asStateFlow()

    private val _gamesLoadFailed = MutableStateFlow(false)
    val gamesLoadFailed: StateFlow<Boolean> = _gamesLoadFailed.asStateFlow()

    init { loadSavedGames() }

    fun loadSavedGames() {
        viewModelScope.launch {
            val loaded = container.loadManualGames()
            if (loaded != null) {
                _games.value = loaded
                _gamesLoaded.value = true
                _gamesLoadFailed.value = false
            } else {
                _gamesLoadFailed.value = true   // 기존 상태 유지 — 실패를 빈 값으로 위장 금지
            }
        }
    }

    /** 게임 수는 설정된 슬롯 수로 자동 반영(원본 `_saveConfig`). 저장 성공 = 로드 상태 확정. */
    fun saveConfig(games: List<List<Int>?>) {
        _games.value = games
        _gamesLoaded.value = true
        _gamesLoadFailed.value = false
        viewModelScope.launch { container.saveManualGames(games) }
    }

    // === 즉시 구매 (번호 탭 저장 버튼 아래 CTA — 스펙 docs/DESIGN-instant-purchase.md) ===

    val isLoggedIn = container.isLoggedIn
    val lastPurchasedRound = container.lastPurchasedRound

    /** 현재 판매 중인 회차(KST). CTA 모드(첫/추가) 분기용. */
    val currentRound: Int get() = PurchaseService.getCurrentRound()

    /**
     * 즉시 구매 다이얼로그 상태머신.
     * Idle → (탭) ConfirmingFirst | PickingExtra | NeedsSetup | SaleClosed
     *      → (확정) InProgress → Success | AlreadyPurchased | SaleClosed | RoundChanged | Error
     *      → (닫기) Idle
     * ConfirmingFirst는 탭 시점 슬롯 스냅샷을 담아 확인창 표시 내용 = 실제 구매 내용을 보장.
     */
    sealed interface InstantState {
        data object Idle : InstantState
        data object NeedsSetup : InstantState         // 저장된 게임 0개 — 설정·저장 안내(스낵바)
        data object StoreError : InstantState         // 저장 슬롯 읽기 실패 — 미설정과 구분(안내 다이얼로그)
        data class ConfirmingFirst(
            val round: Int,
            val autoGames: Int,
            val manualNumbers: List<List<Int>>,
        ) : InstantState {
            val games: Int get() = autoGames + manualNumbers.size
        }
        data class PickingExtra(val round: Int) : InstantState
        data object InProgress : InstantState
        data object AlreadyPurchased : InstantState   // 첫 구매 확정 직전 워커 선점
        data object SaleClosed : InstantState         // 탭·확정 시점 판매시간 재검증 실패
        data object RoundChanged : InstantState       // 확정 회차 ≠ 실제 회차 — 구매 없이 취소
        data class Success(val result: PurchaseResult) : InstantState
        data class Error(val message: String?, val unknown: Boolean) : InstantState
    }

    private val _instantState = MutableStateFlow<InstantState>(InstantState.Idle)
    val instantState: StateFlow<InstantState> = _instantState.asStateFlow()

    /** 지금(KST)이 판매시간인지 — CTA 표시용. 확정 시점에도 재검증한다. */
    fun isSaleOpenNow(): Boolean {
        val now = ZonedDateTime.now(KST)
        return SettingsViewModel.isValidPurchaseTime(now.dayOfWeek.value, now.hour)
    }

    /** 탭 판정 코루틴 핸들 — 이중 탭으로 판정 코루틴이 둘 뜨는 것 자체를 차단. */
    private var tapJob: Job? = null

    /** CTA 탭: 게이트 재검증 후 모드 분기(첫 구매/추가/설정 유도). 저장된 슬롯 기준. */
    fun onInstantTap() {
        if (_instantState.value != InstantState.Idle || tapJob?.isActive == true) return
        tapJob = viewModelScope.launch {
            if (!isSaleOpenNow()) {                                 // 표시가 stale했던 경우 — 사유 표시
                advanceFromIdle(InstantState.SaleClosed)
                return@launch
            }
            runCatching { container.refreshLastPurchasedRound() }
            val round = PurchaseService.getCurrentRound()
            if (container.lastPurchasedRound.value >= round) {
                advanceFromIdle(InstantState.PickingExtra(round))
                return@launch
            }
            val slots = container.loadManualGames()
            when (tapSlotsOutcome(slots)) {
                TapSlotsOutcome.STORE_ERROR -> advanceFromIdle(InstantState.StoreError)
                TapSlotsOutcome.NEEDS_SETUP -> advanceFromIdle(InstantState.NeedsSetup)
                TapSlotsOutcome.PROCEED -> {
                    val (auto, manual) = splitSlots(slots!!)
                    advanceFromIdle(InstantState.ConfirmingFirst(round, auto, manual))
                }
            }
        }
    }

    /**
     * Idle일 때만 상태 전이. 빠른 이중 탭으로 코루틴이 둘 뜨면 늦게 복귀한 쪽이 진행 중
     * 상태(InProgress 등)를 다이얼로그 단계로 되돌려 재확정(=이중 결제) 여지를 만들 수 있어,
     * 상태 기록 직전에 선점 여부를 재확인한다(viewModelScope=Main 단일 스레드라 원자적).
     */
    private fun advanceFromIdle(next: InstantState) {
        if (_instantState.value == InstantState.Idle) _instantState.value = next
    }

    /** 첫 구매 확정 — 탭 시점 스냅샷 그대로 실행. 최종 회차·가드 재판정은 컨테이너 Mutex 안. */
    fun confirmFirst() {
        val s = _instantState.value as? InstantState.ConfirmingFirst ?: return
        launchPurchase {
            container.instantPurchase(
                extra = false, expectedRound = s.round,
                autoGames = s.autoGames, manualNumbers = s.manualNumbers,
            )
        }
    }

    /** 추가 구매 확정 — 자동 [games]게임. 가드로 막지 않음(서버 한도 위임), 회차만 대조. */
    fun confirmExtra(games: Int) {
        val s = _instantState.value as? InstantState.PickingExtra ?: return
        launchPurchase {
            container.instantPurchase(
                extra = true, expectedRound = s.round,
                autoGames = games, manualNumbers = emptyList(),
            )
        }
    }

    fun dismissInstant() {
        if (_instantState.value == InstantState.InProgress) return  // 진행 중 닫기 금지
        _instantState.value = InstantState.Idle
    }

    private fun launchPurchase(block: suspend () -> PurchaseResult?) {
        if (_instantState.value == InstantState.InProgress) return  // 중복 확정 no-op
        if (!isSaleOpenNow()) {                                     // 확정 시점 판매시간 재검증
            _instantState.value = InstantState.SaleClosed
            return
        }
        _instantState.value = InstantState.InProgress
        viewModelScope.launch {
            _instantState.value = try {
                val r = block()
                if (r == null) InstantState.AlreadyPurchased else InstantState.Success(r)
            } catch (e: AppContainer.SaleClosedException) {
                InstantState.SaleClosed                              // 락 대기 중 판매 종료
            } catch (e: AppContainer.RoundChangedException) {
                InstantState.RoundChanged                            // 구매 요청 없이 취소됨
            } catch (e: AppContainer.PurchaseResultUnknownException) {
                InstantState.Error(message = null, unknown = true)
            } catch (e: Exception) {
                InstantState.Error(message = e.message, unknown = false)
            }
        }
    }

    private companion object {
        val KST: ZoneId = ZoneId.of("Asia/Seoul")
    }
}

/** 구매 기록 조회. 진행 중 요청은 계정 변경 시 취소하고 이전 계정의 늦은 응답을 버린다. */
class HistoryViewModel(private val container: AppContainer) : ViewModel() {
    val isLoggedIn = container.isLoggedIn
    private val _purchases = MutableStateFlow<List<Purchase>>(emptyList())
    val purchases: StateFlow<List<Purchase>> = _purchases.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore.asStateFlow()
    private val _loadingAll = MutableStateFlow(false)
    val loadingAll: StateFlow<Boolean> = _loadingAll.asStateFlow()
    private val _canLoadMore = MutableStateFlow(false)
    val canLoadMore: StateFlow<Boolean> = _canLoadMore.asStateFlow()
    private val _windowsLoaded = MutableStateFlow(0)
    val windowsLoaded: StateFlow<Int> = _windowsLoaded.asStateFlow()
    private val _loadedFrom = MutableStateFlow<LocalDate?>(null)
    val loadedFrom: StateFlow<LocalDate?> = _loadedFrom.asStateFlow()
    private val _loadedTo = MutableStateFlow<LocalDate?>(null)
    val loadedTo: StateFlow<LocalDate?> = _loadedTo.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var shownUser: String? = null
    private var loader: HistoryRangeLoader? = null
    private var requestJob: Job? = null

    init {
        viewModelScope.launch {
            combine(container.isLoggedIn, container.loggedInUserId) { logged, user -> logged to user }
                .collect { (logged, user) ->
                    if (!logged) {
                        resetList()
                    } else if (loader == null || shownUser != user) {
                        resetList()
                        shownUser = user
                        loader = HistoryRangeLoader(LocalDate.now(ZoneId.of("Asia/Seoul"))) { from, to ->
                            container.historyService.fetchPurchases(from, to)
                        }
                        loadHistory()
                    }
                }
        }
    }

    private fun resetList() {
        requestJob?.cancel()
        requestJob = null
        loader = null
        shownUser = null
        _purchases.value = emptyList()
        _canLoadMore.value = false
        _windowsLoaded.value = 0
        _loadedFrom.value = null
        _loadedTo.value = null
        _error.value = null
        _loading.value = false
        _loadingMore.value = false
        _loadingAll.value = false
    }

    private fun isCurrent(source: HistoryRangeLoader, user: String?): Boolean =
        loader === source && container.isLoggedIn.value && container.loggedInUserId.value == user

    private fun publish(source: HistoryRangeLoader, user: String?) {
        if (!isCurrent(source, user)) return
        _purchases.value = source.purchases
        _windowsLoaded.value = source.windowsLoaded
        _loadedFrom.value = source.loadedFrom
        _loadedTo.value = source.loadedTo
        _canLoadMore.value = source.canLoadMore
    }

    /** 새로고침도 이미 확인한 기간 전체를 유지한다. 실패한 기간은 기존 내용을 보존한다. */
    fun loadHistory() = runQuery(Query.REFRESH)
    fun loadMore() = runQuery(Query.MORE)
    fun loadAll() = runQuery(Query.ALL)

    private enum class Query { REFRESH, MORE, ALL }

    private fun runQuery(query: Query) {
        val source = loader ?: return
        val user = shownUser
        if (!isCurrent(source, user) || requestJob?.isActive == true) return
        if (query != Query.REFRESH && !source.canLoadMore) return
        _loading.value = query == Query.REFRESH
        _loadingMore.value = query == Query.MORE
        _loadingAll.value = query == Query.ALL
        _error.value = null
        val refreshDate = LocalDate.now(ZoneId.of("Asia/Seoul"))
        requestJob = viewModelScope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            var activeSource = source
            try {
                when (query) {
                    Query.REFRESH -> if (source.today == refreshDate) {
                        source.refresh { publish(source, user) }
                    } else {
                        val replacement = source.refreshedAt(refreshDate)
                        if (isCurrent(source, user)) {
                            loader = replacement
                            activeSource = replacement
                            publish(replacement, user)
                        }
                    }
                    Query.MORE -> {
                        source.loadNext()
                        publish(source, user)
                    }
                    Query.ALL -> while (source.canLoadMore && isCurrent(source, user)) {
                        source.loadNext()
                        publish(source, user)
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (isCurrent(source, user)) {
                    _error.value = "구매 내역을 불러오지 못했습니다. 다시 시도해 주세요."
                }
            } finally {
                if (loader === activeSource) {
                    _loading.value = false
                    _loadingMore.value = false
                    _loadingAll.value = false
                }
            }
        }.also { it.start() }
    }
}

/** 설정: 로그인/로그아웃·잔액·자동구매 설정·언어·잔액알림·초기화. */
class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val isLoggedIn = container.isLoggedIn
    val balance = container.balance
    val autoEnabled = container.autoEnabled
    val autoGames = container.autoGames
    val autoPurchaseDay = container.autoPurchaseDay
    val autoPurchaseHour = container.autoPurchaseHour
    val autoPurchaseMinute = container.autoPurchaseMinute
    val balanceAlertEnabled = container.balanceAlertEnabled
    val balanceAlertThreshold = container.balanceAlertThreshold
    val language = container.language
    val loggedInUserId = container.loggedInUserId

    /** 로그인 진행/결과 (스낵바·다이얼로그용). 원본 INVALID_CREDENTIALS 분기 유지. */
    sealed interface LoginState {
        data object Idle : LoginState
        data object InProgress : LoginState
        data object Success : LoginState
        data object InvalidCredentials : LoginState
        data object Error : LoginState
    }

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    fun login(id: String, pw: String) {
        val u = id.trim()
        val p = pw.trim()
        if (u.isEmpty() || p.isEmpty()) return
        viewModelScope.launch {
            _loginState.value = LoginState.InProgress
            _loginState.value = try {
                container.login(u, p)
                LoginState.Success
            } catch (e: Exception) {
                if ((e.message ?: "").contains("INVALID_CREDENTIALS")) LoginState.InvalidCredentials
                else LoginState.Error
            }
        }
    }

    /** 스낵바 표시 후 상태 소비. */
    fun consumeLoginState() { _loginState.value = LoginState.Idle }

    fun logout() { viewModelScope.launch { container.logout() } }
    fun refreshBalance() { viewModelScope.launch { container.refreshBalance() } }

    fun setAutoEnabled(v: Boolean) { viewModelScope.launch { container.setAutoEnabled(v) } }

    /** 요일 변경. 구매 불가 시간이면 false 반환(원본 검증 스낵바). */
    fun setPurchaseDay(day: Int): Boolean {
        if (!isValidPurchaseTime(day, autoPurchaseHour.value)) return false
        viewModelScope.launch { container.setAutoPurchaseDay(day) }
        return true
    }

    /** 시간 변경. 구매 불가 시간이면 false 반환. */
    fun setPurchaseTime(hour: Int, minute: Int): Boolean {
        if (!isValidPurchaseTime(autoPurchaseDay.value, hour)) return false
        viewModelScope.launch { container.setAutoPurchaseTime(hour, minute) }
        return true
    }

    fun setLanguage(lang: String) { viewModelScope.launch { container.setLanguage(lang) } }
    fun setBalanceAlertEnabled(v: Boolean) { viewModelScope.launch { container.setBalanceAlertEnabled(v) } }
    fun setBalanceAlertThreshold(v: Int) { viewModelScope.launch { container.setBalanceAlertThreshold(v) } }
    fun resetAll() { viewModelScope.launch { container.resetAll() } }

    companion object {
        /**
         * 구매 가능 시간 (원본 `_isValidPurchaseTime`). day: 1=월 .. 7=일.
         * 토(6): 06:00~19:59, 그 외(평일/일): 06:00~23:59. (토 20:00~일 05:59 판매정지)
         */
        fun isValidPurchaseTime(day: Int, hour: Int): Boolean =
            if (day == 6) hour in 6..19 else hour >= 6
    }
}
