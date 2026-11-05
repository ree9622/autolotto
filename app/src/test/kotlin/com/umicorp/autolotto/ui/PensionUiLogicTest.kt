package com.umicorp.autolotto.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.umicorp.autolotto.data.Rank720
import com.umicorp.autolotto.data.Slot720
import com.umicorp.autolotto.data.Ticket720
import com.umicorp.autolotto.data.WinningNumbers720
import com.umicorp.autolotto.ui.screen.PensionDigitColors
import com.umicorp.autolotto.ui.screen.SlotEditMode
import com.umicorp.autolotto.ui.screen.buildPensionSlot
import com.umicorp.autolotto.ui.vm.pensionHistoryWindows
import com.umicorp.autolotto.ui.vm.pensionRoundGroups
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class PensionUiLogicTest {
    private val date = LocalDateTime.of(2026, 11, 6, 12, 0)
    private fun ticket(round: Int, jo: Int, number: String, rank: Rank720? = null) =
        Ticket720(round, jo, number, rank, purchaseDate = date)

    @Test
    fun `history windows cover one year without gaps or overlap inside the three month limit`() {
        val today = LocalDate.of(2026, 11, 5)
        val windows = pensionHistoryWindows(today)

        assertEquals(4, windows.size)
        assertEquals(today, windows.first().second)
        assertEquals(today.minusYears(1), windows.last().first)
        windows.forEach { (from, to) ->
            assertFalse(from.isAfter(to))
            assertTrue(ChronoUnit.DAYS.between(from, to) <= 92)
        }
        windows.zipWithNext().forEach { (newer, older) -> assertEquals(newer.first.minusDays(1), older.second) }
    }

    @Test
    fun `history windows stay valid at month ends`() {
        val windows = pensionHistoryWindows(LocalDate.of(2026, 5, 31))
        windows.forEach { (from, to) -> assertFalse(from.isAfter(to)) }
        windows.zipWithNext().forEach { (newer, older) -> assertFalse(older.second.isAfter(newer.first)) }
    }

    @Test
    fun `round groups are newest first and ranked per ticket`() {
        val winning = WinningNumbers720(339, 3, "604270", "111333", "2026-10-29")
        val groups = pensionRoundGroups(
            listOf(ticket(339, 3, "604270"), ticket(340, 1, "123456"), ticket(339, 1, "999990")),
            mapOf(339 to winning),
        )

        assertEquals(listOf(340, 339), groups.map { it.round })
        assertEquals(listOf(Rank720.PENDING), groups[0].ranks)
        assertFalse(groups[0].won)
        assertEquals(listOf(Rank720.FIRST, Rank720.SEVENTH), groups[1].ranks)
        assertTrue(groups[1].won)
        assertEquals(1_000L, groups[1].lumpTotal)
    }

    @Test
    fun `round group falls back to the history rank when numbers could not be loaded`() {
        val group = pensionRoundGroups(listOf(ticket(330, 2, "111111", Rank720.THIRD)), emptyMap()).single()
        assertEquals(Rank720.THIRD, group.ranks.single())
        assertNull(group.winning)
    }

    @Test
    fun `slot editor builds only valid slots`() {
        assertEquals(Slot720.Unset, buildPensionSlot(SlotEditMode.NONE, 1, ""))
        assertEquals(Slot720.FullAuto, buildPensionSlot(SlotEditMode.AUTO, 3, "12"))
        assertEquals(Slot720.SemiAuto(3), buildPensionSlot(SlotEditMode.GROUP, 3, ""))
        assertEquals(Slot720.Manual(2, listOf(0, 1, 2, 3, 4, 5)), buildPensionSlot(SlotEditMode.MANUAL, 2, "012345"))
        assertNull(buildPensionSlot(SlotEditMode.MANUAL, 2, "12345"))
        assertNull(buildPensionSlot(SlotEditMode.MANUAL, 2, "12345a"))
    }

    @Test
    fun `digit ball colors keep readable contrast with white text`() {
        assertEquals(6, PensionDigitColors.size)
        PensionDigitColors.forEach { color ->
            val contrast = (Color.White.luminance() + 0.05f) / (color.luminance() + 0.05f)
            assertTrue("$color 명암비 $contrast", contrast >= 4.5f)
        }
    }
}
