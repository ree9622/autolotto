package com.umicorp.autolotto.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class HistorySummaryTest {
    private fun purchase(round: Int, checked: Boolean = true, rank: String? = "nowin", prize: Long = 0L, amount: Int = 1000) =
        Purchase(round, LocalDateTime.of(2026, 10, 19, 0, 0), emptyList(), 1, 0, amount, checked, rank, prize)

    @Test
    fun `empty history has zero totals`() {
        assertEquals(HistorySummary(0, 0, 0, 0, 0), summarizeHistory(emptyList()))
    }

    @Test
    fun `totals use Long and count distinct winning and pending rounds`() {
        val history = listOf(
            purchase(1, rank = "rank1", prize = 3_000_000_000L, amount = Int.MAX_VALUE),
            purchase(1, rank = "rank5", prize = 5000L),
            purchase(2, checked = false),
            purchase(2, checked = false),
            purchase(3),
        )
        val summary = summarizeHistory(history)
        assertEquals(Int.MAX_VALUE.toLong() + 4000L, summary.totalPurchaseAmount)
        assertEquals(3_000_005_000L, summary.totalPrizeAmount)
        assertEquals(summary.totalPrizeAmount - summary.totalPurchaseAmount, summary.netAmount)
        assertEquals(1, summary.winningRoundCount)
        assertEquals(1, summary.pendingRoundCount)
    }

    @Test
    fun `only valid winning rank or positive confirmed prize is winning`() {
        assertTrue(purchase(1, rank = "rank5").isWinning)
        assertTrue(purchase(1, prize = 5000).isWinning)
        assertTrue(purchase(1).copy(gameRanks = listOf("nowin", "rank4")).isWinning)
        assertFalse(purchase(1).isWinning)
        assertFalse(purchase(1, rank = "unknown").isWinning)
        assertFalse(purchase(1, checked = false, rank = "pending", prize = 5000).isWinning)
        assertEquals(0L, summarizeHistory(listOf(purchase(1, checked = false, prize = 5000))).totalPrizeAmount)
    }
}
