package com.umicorp.autolotto

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.umicorp.autolotto.data.FallbackPolicy
import com.umicorp.autolotto.data.NumberConfig720
import com.umicorp.autolotto.data.Rank720
import com.umicorp.autolotto.data.Slot720
import com.umicorp.autolotto.data.Ticket720
import com.umicorp.autolotto.data.WinningNumbers720
import com.umicorp.autolotto.dhlottery.DhlotteryException
import com.umicorp.autolotto.dhlottery.PartialFailure
import com.umicorp.autolotto.dhlottery.PurchaseResult720
import com.umicorp.autolotto.ui.LocalizedApp
import com.umicorp.autolotto.ui.screen.PensionActions
import com.umicorp.autolotto.ui.screen.PensionPage
import com.umicorp.autolotto.ui.screen.PensionPageContent
import com.umicorp.autolotto.ui.Product
import com.umicorp.autolotto.ui.ProductSwitcher
import androidx.compose.runtime.mutableStateOf
import com.umicorp.autolotto.ui.screen.PensionUiState
import com.umicorp.autolotto.ui.theme.AutoLottoTheme
import com.umicorp.autolotto.ui.vm.PensionBuyState
import org.junit.Assert.assertEquals
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDateTime

/** 계정·네트워크 없이 합성 자료로 연금 탭의 상태별 화면을 띄운다. 결제 호출은 가짜 콜백까지만 간다. */
@OptIn(ExperimentalTestApi::class)
class PensionScreenTest {
    @get:Rule val compose = createComposeRule()

    private val winning = WinningNumbers720(340, 3, "604270", "111333", "2026-11-05")
    private val config = NumberConfig720.empty().copy(
        slots = listOf(
            Slot720.FullAuto,
            Slot720.SemiAuto(2),
            Slot720.Manual(4, listOf(0, 1, 2, 3, 4, 5)),
            Slot720.Unset,
            Slot720.Unset,
        ),
    )
    private val state = PensionUiState(
        isLoggedIn = true, balance = 10_000, config = config, configLoaded = true, upcomingRound = 341,
        saleOpen = true, purchasedRound = 0, winning = winning, winningLoading = false, autoEnabled = true,
        autoDay = 5, autoHour = 9, autoMinute = 0, tickets = emptyList(), winningByRound = emptyMap(),
        historyLoading = false, historyFailed = false, canLoadMore = false,
    )
    private val date = LocalDateTime.of(2026, 11, 1, 12, 0)

    private fun show(
        value: PensionUiState = state,
        buy: PensionBuyState = PensionBuyState.Idle,
        actions: PensionActions = PensionActions(),
        size: DpSize = DpSize(380.dp, 820.dp),
        fontScale: Float = 1f,
        dark: Boolean = false,
        page: PensionPage = PensionPage.NUMBERS,
    ) {
        pageState.value = page
        compose.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(size).then(DeviceConfigurationOverride.FontScale(fontScale)),
            ) {
                AutoLottoTheme(darkTheme = dark) {
                    LocalizedApp("ko") { PensionPageContent(pageState.value, value, buy, actions) }
                }
            }
        }
    }

    /** 탭 전환 대신 같은 상태로 장만 바꾼다(앱에서는 하단 탭이 바꾼다). */
    private val pageState = mutableStateOf(PensionPage.NUMBERS)

    private fun openPage(page: PensionPage) {
        compose.runOnIdle { pageState.value = page }
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        // 대화상자가 떠 있으면 별도 창이라 그 창을 찍는다.
        val dialogs = compose.onAllNodes(isDialog()).fetchSemanticsNodes()
        val node = if (dialogs.isEmpty()) compose.onRoot() else compose.onNode(isDialog())
        val bitmap = node.captureToImage().asAndroidBitmap()
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("pension-proof")!!
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** 좁은 화면은 통계 칸 아래에 카드가 있어 목록을 그 자리까지 내린 뒤 확인한다. */
    private fun scrollHistoryTo(text: String) {
        compose.onNodeWithTag("pension-history").performScrollToNode(hasText(text))
    }

    @Test fun homeShowsWinningStatusAndLeadsToNumbers() {
        var numbers = 0
        var deposit = 0
        show(
            state.copy(purchasedRound = 341),
            actions = PensionActions(onNavigateToNumbers = { numbers++ }, onNavigateToDeposit = { deposit++ }),
            page = PensionPage.HOME,
        )
        compose.onNodeWithText("연금복권720+").assertExists()
        compose.onNodeWithContentDescription("3조 604270").assertExists()
        compose.onNodeWithContentDescription("보너스 각 조 111333").assertExists()
        compose.onNodeWithText("다음 추첨: 제 341회 · 2026-11-12(목) 19:05").assertExists()
        compose.onNodeWithText("이번 회차 구매 완료").assertExists()
        compose.onNodeWithText("₩10,000").assertExists()
        compose.onNodeWithText("자동 구매 활성화").performScrollTo().assertExists()
        capture("home-380")
        compose.onNodeWithTag("pension-home-cta").performScrollTo().performClick()
        assertEquals(1, numbers)
    }

    @Test fun homeAutoOffPointsToNumbersTab() {
        show(state.copy(autoEnabled = false), page = PensionPage.HOME)
        compose.onNodeWithText("번호 탭에서 켤 수 있습니다").performScrollTo().assertExists()
    }

    @Test fun productSwitcherSelectsLottery() {
        val picked = mutableListOf<Product>()
        compose.setContent {
            AutoLottoTheme { LocalizedApp("ko") { ProductSwitcher(Product.LOTTO) { picked += it } } }
        }
        compose.onNodeWithText("로또 6/45").assertExists()
        compose.onNodeWithTag("product-pension").performClick()
        assertEquals(listOf(Product.PENSION), picked)
    }

    @Test fun buyPaneShowsNumbersAmountAndReadsNumberAsOneRow() {
        show()
        compose.onNodeWithText("번호 설정").assertExists()
        compose.onNodeWithContentDescription("3조 604270").assertDoesNotExist()   // 당첨번호는 홈에 있다.
        compose.onNodeWithTag("pension-buy-cta").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("3,000원 구매하기").assertExists()
        compose.onNodeWithText("이미 팔린 번호라면").assertExists()
    }

    @Test fun buyButtonExplainsWhyItIsLocked() {
        show(state.copy(isLoggedIn = false, autoEnabled = false))
        compose.onNodeWithTag("pension-buy-cta").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("설정에서 로그인").assertExists()
        capture("logged-out-380")
    }

    @Test fun zeroGamesAndSaleClosedHaveOwnReasons() {
        show(state.copy(config = NumberConfig720.empty(), autoEnabled = false))
        compose.onNodeWithTag("pension-buy-cta").performScrollTo().assertIsNotEnabled()
        capture("no-games-380")
    }

    @Test fun saleClosedLabel() {
        show(state.copy(saleOpen = false))
        compose.onNodeWithTag("pension-buy-cta").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("지금은 판매 시간이 아닙니다").assertExists()
    }

    @Test fun slotEditorAcceptsOnlySixDigitsAndSaves() {
        var saved: List<Slot720>? = null
        show(actions = PensionActions(onSaveConfig = { slots, _, _ -> saved = slots }))
        compose.onNodeWithTag("pension-slot-3").performScrollTo().performClick()
        compose.onNodeWithText("번호 지정").performClick()
        compose.onNodeWithTag("pension-slot-confirm").assertIsNotEnabled()
        compose.onNodeWithTag("pension-digits").performTextInput("12a34")
        compose.onNodeWithTag("pension-slot-confirm").assertIsNotEnabled()
        capture("slot-editor-380")
        compose.onNodeWithTag("pension-digits").performTextInput("567")
        compose.onNodeWithTag("pension-slot-confirm").assertIsEnabled().performClick()
        assertEquals(Slot720.Manual(1, listOf(1, 2, 3, 4, 5, 6)), saved!![3])
    }

    @Test fun setModeHidesSlotsAndChargesFiveGames() {
        show(state.copy(config = config.copy(setMode = true, fallback = FallbackPolicy.GIVE_UP)))
        compose.onNodeWithText("5,000원 구매하기").performScrollTo()
        compose.onNodeWithTag("pension-slot-0").assertDoesNotExist()
        compose.onNodeWithText("이미 팔린 번호라면").assertDoesNotExist()
        compose.onNodeWithTag("pension-set-preview").performScrollTo().assertExists()
        compose.onNodeWithText("다섯 장 모두 같은 번호입니다.").assertExists()
        capture("set-mode-380")
    }

    @Test fun buyModeSelectorSwitchesBetweenSeparateAndSet() {
        var savedSet: Boolean? = null
        show(state, actions = PensionActions(onSaveConfig = { _, _, set -> savedSet = set }))
        compose.onNodeWithTag("pension-mode-separate").assertIsSelected()
        compose.onNodeWithText("완전 자동으로 두면 게임마다 다른 번호", substring = true).assertExists()
        compose.onNodeWithTag("pension-set-preview").assertDoesNotExist()
        compose.onNodeWithTag("pension-mode-separate").performClick()
        assertEquals(null, savedSet)
        compose.onNodeWithTag("pension-mode-set").performClick()
        assertEquals(true, savedSet)
    }

    @Test fun confirmDialogShowsWhatWillBePaidAndPaysOnce() {
        var paid = 0
        show(
            buy = PensionBuyState.ConfirmingFirst(341, config),
            actions = PensionActions(onConfirmFirst = { paid++ }),
        )
        compose.onNodeWithText("제 341회 · 3게임 · 3,000원").assertExists()
        compose.onNodeWithText("A  완전 자동").assertExists()
        compose.onNodeWithText("B  2조 · 번호 자동").assertExists()
        compose.onNodeWithText("C  4조 012345").assertExists()
        capture("confirm-380")
        compose.onNodeWithTag("pension-confirm-pay").performClick()
        assertEquals(1, paid)
    }

    @Test fun extraPurchaseOffersGamesAndSet() {
        var games = 0
        var set = 0
        show(
            state.copy(purchasedRound = 341),
            PensionBuyState.PickingExtra(341),
            PensionActions(onConfirmExtraAuto = { games = it }, onConfirmExtraSet = { set++ }),
        )
        compose.onNodeWithText("3게임").performClick()
        compose.onNodeWithText("3,000원 결제").performClick()
        assertEquals(3, games)
        compose.onNodeWithTag("pension-extra-set").performClick()
        compose.onNodeWithText("5,000원 결제").performClick()
        assertEquals(1, set)
    }

    @Test fun resultUnknownOffersHistoryOnlyAndNoRetry() {
        var dismissed = 0
        var history = 0
        show(
            buy = PensionBuyState.ResultUnknown,
            actions = PensionActions(onDismissBuy = { dismissed++ }, onNavigateToHistory = { history++ }),
        )
        compose.onNodeWithText("다시 시도").assertDoesNotExist()
        capture("unknown-380")
        compose.onNodeWithTag("pension-unknown-history").performClick()
        assertEquals(1, dismissed)
        assertEquals(1, history)
    }

    @Test fun inProgressCannotBeDismissedByActions() {
        show(buy = PensionBuyState.InProgress)
        compose.onNodeWithText("구매하는 중입니다. 앱을 닫지 말아주세요.").assertExists()
        compose.onNodeWithText("확인").assertDoesNotExist()
        compose.onNodeWithText("취소").assertDoesNotExist()
    }

    @Test fun successShowsTicketsAndWarnings() {
        val result = PurchaseResult720(
            341,
            listOf(Ticket720(341, 2, "012345", purchaseDate = date)),
            1000,
            PartialFailure(2, DhlotteryException("번호 확인 실패")),
        )
        show(buy = PensionBuyState.Success(result, recordFailed = true))
        compose.onNodeWithContentDescription("2조 012345").assertExists()
        compose.onNodeWithText("나머지 2게임은 구매되지 않았습니다.").assertExists()
        compose.onNodeWithText("구매 기록을 저장하지 못해 자동 구매를 껐습니다. 내역을 확인한 뒤 다시 켜주세요.").assertExists()
        capture("success-380")
    }

    @Test fun confirmedByHistorySaysItIsTheWholeRound() {
        val result = PurchaseResult720(
            341,
            listOf(Ticket720(341, 2, "012345", purchaseDate = date)),
            1000,
            confirmedByHistory = true,
        )
        show(buy = PensionBuyState.Success(result))
        compose.onNodeWithText("구매 내역으로 확인했습니다. 아래는 제 341회 전체 1게임입니다.").assertExists()
    }

    @Test fun historyGroupsRoundsRanksAndTotals() {
        show(
            state.copy(
                tickets = listOf(
                    Ticket720(341, 1, "123456", purchaseDate = date),
                    Ticket720(340, 3, "604270", purchaseDate = date),
                    Ticket720(340, 1, "004270", purchaseDate = date),
                    Ticket720(340, 5, "999999", purchaseDate = date),
                ),
                winningByRound = mapOf(340 to winning),
                canLoadMore = true,
            ),
        )
        openPage(PensionPage.HISTORY)
        capture("history-380")
        scrollHistoryTo("제 341회")
        compose.onNodeWithText("추첨 전").assertExists()
        scrollHistoryTo("일시금 합계 1,000,000원")
        compose.onNodeWithText("1등").assertExists()
        compose.onNodeWithText("월 700만원 × 20년").assertExists()
        compose.onNodeWithText("3등").assertExists()
        compose.onNodeWithText("연금식 당첨 1건").assertExists()
        compose.onNodeWithText("이 회차 당첨번호").assertExists()
        capture("history-380-card")
    }

    @Test fun historyEmptyStatesLeadSomewhere() {
        var settings = 0
        show(
            state.copy(isLoggedIn = false, autoEnabled = false),
            actions = PensionActions(onNavigateToSettings = { settings++ }),
        )
        openPage(PensionPage.HISTORY)
        compose.onNodeWithText("로그인하면 구매 내역을 볼 수 있습니다.").assertExists()
        compose.onNodeWithText("설정에서 로그인").performClick()
        assertEquals(1, settings)
    }

    @Test fun historyEmptyGoesBackToBuy() {
        var numbers = 0
        show(actions = PensionActions(onNavigateToNumbers = { numbers++ }))
        openPage(PensionPage.HISTORY)
        scrollHistoryTo("최근 1년 구매 내역이 아직 없습니다.")
        capture("history-empty-380")
        compose.onNodeWithText("번호 고르러 가기").performClick()
        assertEquals(1, numbers)
    }

    @Test fun historyFailureOffersRetryAndKeepsTickets() {
        var retried = 0
        show(
            state.copy(
                tickets = listOf(Ticket720(341, 1, "123456", purchaseDate = date)),
                historyFailed = true,
            ),
            actions = PensionActions(onRefreshHistory = { retried++ }),
        )
        openPage(PensionPage.HISTORY)
        compose.onNodeWithText("내역을 불러오지 못했습니다.").assertExists()
        compose.onNodeWithText("다시 시도").performClick()
        assertEquals(1, retried)
        compose.onNodeWithTag("pension-history").performScrollToNode(hasContentDescription("1조 123456"))
    }

    @Test fun winningFailureOffersRetry() {
        var retried = 0
        show(state.copy(winning = null), actions = PensionActions(onRetryWinning = { retried++ }), page = PensionPage.HOME)
        compose.onNodeWithText("당첨번호를 불러오지 못했습니다.").assertExists()
        compose.onNodeWithText("다시 시도").performClick()
        assertEquals(1, retried)
    }

    @Test fun blockedScheduleShowsMessage() {
        show(actions = PensionActions(onSetAutoTime = { _, _ -> false }, onSetAutoDay = { false }))
        compose.onNodeWithText("금요일").performScrollTo().performClick()
        compose.onNodeWithText("목요일").performClick()
        compose.onNodeWithText("목요일 17시~22시는 판매가 멈춥니다. 다른 시간을 골라주세요.").assertExists()
    }

    private val historyState = state.copy(
        tickets = listOf(
            Ticket720(341, 1, "123456", purchaseDate = date),
            Ticket720(340, 3, "604270", purchaseDate = date),
            Ticket720(340, 1, "004270", purchaseDate = date),
            Ticket720(339, 2, "111111", Rank720.NONE, purchaseDate = date),
        ),
        winningByRound = mapOf(340 to winning),
        canLoadMore = true,
        windowsLoaded = 1,
        loadedFrom = LocalDate.of(2026, 8, 6),
        loadedTo = LocalDate.of(2026, 11, 6),
    )

    private fun inSummary(text: String) {
        compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("pension-history-summary"))).assertExists()
    }

    @Test fun historySummaryShowsTotalsAndAnnuitySeparately() {
        show(historyState)
        openPage(PensionPage.HISTORY)
        compose.onNodeWithText("2026-08-06 ~ 2026-11-06").assertExists()
        inSummary("4,000원")        // 4장 구매
        inSummary("1,000,000원")    // 3등 일시금
        inSummary("996,000원")      // 손익
        compose.onNodeWithText("연금식 당첨 1건 · 매달 받는 당첨금이라 합계에서 뺐습니다").assertExists()
        compose.onNodeWithText("당첨 1회 · 추첨 대기 1회").assertExists()
        compose.onNodeWithText("일부 기간만 집계 · 1/4 기간 완료").assertExists()
    }

    @Test fun historyFiltersNarrowRounds() {
        show(historyState)
        openPage(PensionPage.HISTORY)
        compose.onNodeWithText("3개 회차").assertExists()
        compose.onNodeWithTag("pension-filter-winning").performClick()
        compose.onNodeWithText("1개 회차").assertExists()
        scrollHistoryTo("제 340회")
        compose.onNodeWithText("제 341회").assertDoesNotExist()
        compose.onNodeWithTag("pension-filter-pending").performClick()
        scrollHistoryTo("제 341회")
        compose.onNodeWithText("제 340회").assertDoesNotExist()
        capture("history-filter-pending-380")
    }

    @Test fun historyLoadAllAndRefreshCallActions() {
        var all = 0
        var refresh = 0
        show(historyState, actions = PensionActions(onLoadAllHistory = { all++ }, onRefreshHistory = { refresh++ }))
        compose.onNodeWithTag("pension-history-refresh").assertDoesNotExist()   // 구매 화면에는 없다.
        openPage(PensionPage.HISTORY)
        compose.onNodeWithTag("pension-history-load-all").performClick()
        compose.onNodeWithTag("pension-history-refresh").performClick()
        assertEquals(1, all)
        assertEquals(1, refresh)
    }

    @Test fun historyLoadingAllShowsProgressAndDisablesButton() {
        show(historyState.copy(historyLoading = true, loadingAll = true, historyProgress = 2))
        openPage(PensionPage.HISTORY)
        compose.onNodeWithText("전체 내역 불러오는 중 · 2/4 기간 완료").assertExists()
        compose.onNodeWithTag("pension-history-load-all").assertIsNotEnabled()
    }

    @Test fun historyWideUsesTwoPanes() {
        show(historyState, size = DpSize(1180.dp, 820.dp))
        openPage(PensionPage.HISTORY)
        compose.onNodeWithTag("pension-history-wide").assertExists()
        compose.onNodeWithTag("pension-history-summary").assertExists()
        compose.onNodeWithText("제 341회").assertExists()
        capture("history-wide-1180")
    }

    private fun viewport(name: String, width: Int, height: Int, fontScale: Float = 1f, dark: Boolean = false) {
        show(
            state.copy(
                tickets = listOf(Ticket720(340, 3, "604270", purchaseDate = date)),
                winningByRound = mapOf(340 to winning),
            ),
            size = DpSize(width.dp, height.dp),
            fontScale = fontScale,
            dark = dark,
            page = PensionPage.HOME,
        )
        compose.onNodeWithContentDescription("3조 604270").assertExists()
        capture("$name-home")
        openPage(PensionPage.NUMBERS)
        compose.onNodeWithTag("pension-buy-cta").assertExists()
        capture(name)
        openPage(PensionPage.HISTORY)
        capture("$name-history")
        scrollHistoryTo("1등")
        capture("$name-history-card")
    }

    @Test fun viewportFolded() = viewport("fold-360", 360, 780)
    @Test fun viewportUnfolded() = viewport("unfold-884", 884, 1000)
    @Test fun viewportTabletPortrait() = viewport("tablet-820", 820, 1100)
    @Test fun viewportTabletLandscape() = viewport("tablet-1180", 1180, 820)
    @Test fun viewportLargeFont() = viewport("fold-360-font2x", 360, 780, fontScale = 2f)
    @Test fun viewportDark() = viewport("fold-360-dark", 360, 780, dark = true)
}
