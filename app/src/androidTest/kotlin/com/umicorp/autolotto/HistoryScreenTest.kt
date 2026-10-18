package com.umicorp.autolotto

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.umicorp.autolotto.data.Purchase
import com.umicorp.autolotto.ui.screen.HistoryContent
import com.umicorp.autolotto.ui.LocalizedApp
import com.umicorp.autolotto.ui.theme.AutoLottoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

@OptIn(ExperimentalTestApi::class)
class HistoryScreenTest {
    @get:Rule val compose = createComposeRule()
    private val size = mutableStateOf(DpSize(380.dp, 820.dp))
    private val fontScale = mutableStateOf(1f)
    private val records = listOf(
        record(1234, true, "rank5", 5000),
        record(1233, true, "nowin", 0),
        record(1232, false, null, 0),
    )

    private fun show(
        values: List<Purchase> = records, complete: Boolean = true, error: String? = null,
        onAll: () -> Unit = {}, loggedIn: Boolean = true,
    ) {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size.value)
                .then(DeviceConfigurationOverride.FontScale(fontScale.value))) {
                AutoLottoTheme { LocalizedApp("ko") {
                    HistoryContent(values, false, false, false, !complete, error, loggedIn,
                        if (complete) 4 else 1, LocalDate.of(2025, 10, 19), LocalDate.of(2026, 10, 19),
                        {}, {}, onAll)
                } }
            }
        }
    }

    @Test fun compactFilterAndResizePreserveSelectionAndTotals() {
        show()
        compose.onNodeWithTag("history-compact").assertExists()
        compose.onNodeWithText("3,000원").assertExists()
        compose.onNodeWithTag("history-list").performScrollToNode(
            androidx.compose.ui.test.hasTestTag("history-filter-winning"))
        compose.onNodeWithTag("history-filter-winning").performClick()
        compose.onNodeWithText("1개 회차").assertExists()
        capture("history-folded")
        compose.runOnIdle { size.value = DpSize(840.dp, 900.dp) }
        compose.onNodeWithTag("history-wide").assertExists()
        compose.onNodeWithText("1개 회차").assertExists()
        compose.onNodeWithText("3,000원").assertExists()
        compose.onNodeWithText("제 1234회").assertExists()
        compose.onNodeWithText("제 1233회").assertDoesNotExist()
        capture("history-unfolded")
        compose.onNodeWithTag("history-filter-all").performClick()
        compose.onNodeWithText("3개 회차").assertExists()
        compose.onNodeWithTag("history-filter-pending").performClick()
        compose.onNodeWithText("1개 회차").assertExists()
        compose.onNodeWithText("제 1232회").assertExists()
    }

    @Test fun emptyWinningFilterStillAllowsFullHistoryLoad() {
        size.value = DpSize(840.dp, 900.dp)
        var calls = 0
        show(values = listOf(record(1233, true, "nowin", 0)), complete = false, onAll = { calls++ })
        compose.onNodeWithTag("history-filter-winning").performClick()
        compose.onNodeWithText("불러온 기간에 당첨 내역이 없습니다.").assertIsDisplayed()
        compose.onNodeWithTag("history-load-all").performClick()
        compose.runOnIdle { assertEquals(1, calls) }
    }

    @Test fun failureShowsPartialTotalsAndRetry() {
        size.value = DpSize(840.dp, 900.dp)
        var calls = 0
        show(complete = false, error = "synthetic failure", onAll = { calls++ })
        compose.onNodeWithText("일부 기간만 집계 · 1/4 기간 완료").assertExists()
        compose.onNodeWithText("다시 불러오기").performClick()
        compose.runOnIdle { assertEquals(1, calls) }
        compose.onNodeWithText("synthetic failure").assertDoesNotExist()
    }

    @Test fun signedOutShowsLoginInsteadOfZeroTotals() {
        show(values = emptyList(), loggedIn = false)
        compose.onNodeWithText("구매내역 통계").assertDoesNotExist()
        compose.onNodeWithTag("history-filter-winning").assertDoesNotExist()
        capture("history-signed-out")
    }

    @Test fun narrowScreenWithLargeTextKeepsRecordsScrollable() {
        size.value = DpSize(320.dp, 720.dp)
        fontScale.value = 1.4f
        show(values = listOf(record(1234, true, "rank1", 3_000_000_000)))
        compose.onNodeWithTag("history-list").performScrollToNode(
            androidx.compose.ui.test.hasText("제 1234회"))
        compose.onNodeWithText("제 1234회").assertIsDisplayed()
        capture("history-large-text")
    }

    private fun capture(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("history-proof")!!
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun record(round: Int, checked: Boolean, rank: String?, prize: Long) = Purchase(
        round, LocalDate.of(2026, 10, 17).atStartOfDay(), listOf(listOf(1, 2, 3, 4, 5, 6)),
        1, 0, 1000, checked, rank, prize, listOf(rank ?: "pending"), listOf(prize),
    )
}
