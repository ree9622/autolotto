package com.umicorp.autolotto.scheduler

import com.umicorp.autolotto.data.Rank720
import com.umicorp.autolotto.data.Ticket720
import com.umicorp.autolotto.data.WinningNumbers720
import com.umicorp.autolotto.dhlottery.DhlotteryException
import com.umicorp.autolotto.dhlottery.PartialFailure
import com.umicorp.autolotto.dhlottery.PensionOutcome
import com.umicorp.autolotto.dhlottery.PurchaseResult720
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class PensionNoticeTest {
    private val date = LocalDateTime.of(2026, 11, 6, 12, 0)
    private val winning = WinningNumbers720(340, 3, "604270", "111333", "2026-11-05")
    private fun ticket(jo: Int, number: String, round: Int = 340, rank: Rank720? = null) =
        Ticket720(round, jo, number, rank, purchaseDate = date)

    @Test
    fun `no tickets in the round only announces the numbers`() {
        val notice = pensionResultNotice(340, winning, listOf(ticket(1, "000000", round = 339)))
        assertTrue(notice.title.contains("당첨번호"))
        assertTrue(notice.body.contains("3조 604270"))
    }

    @Test
    fun `tickets are ranked against the winning numbers and lump sums added`() {
        val notice = pensionResultNotice(
            340,
            winning,
            listOf(ticket(1, "604270"), ticket(2, "004270"), ticket(4, "999999"), ticket(5, "111330")),
        )
        assertTrue(notice.title.contains("당첨"))
        assertTrue(notice.body.contains("1조 604270 → 2등 (월 100만원×10년)"))
        assertTrue(notice.body.contains("2조 004270 → 3등"))
        assertTrue(notice.body.contains("4조 999999 → 낙첨"))
        assertTrue(notice.body.contains("일시금 합계: ₩1,001,000"))
    }

    @Test
    fun `a stale pending rank from history does not read as a loss`() {
        val notice = pensionResultNotice(340, winning, listOf(ticket(3, "604270", rank = Rank720.PENDING)))
        assertTrue(notice.body.contains("1등"))
    }

    @Test
    fun `all losing tickets say so without a total`() {
        val notice = pensionResultNotice(340, winning, listOf(ticket(1, "999999")))
        assertTrue(notice.title.contains("낙첨"))
        assertFalse(notice.body.contains("일시금 합계"))
    }

    @Test
    fun `purchase notice lists tickets and warns about the remainder`() {
        val result = PurchaseResult720(
            340,
            listOf(ticket(1, "123456")),
            1000,
            PartialFailure(2, DhlotteryException("번호 확인 실패")),
        )
        val body = pensionAutoNotice(PensionOutcome.Purchased(result), isLastAttempt = false)!!.body
        assertTrue(body.contains("1조 123456"))
        assertTrue(body.contains("나머지 2게임은 구매되지 않았습니다"))
    }

    @Test
    fun `unknown remainder asks to check history instead of saying not bought`() {
        val result = PurchaseResult720(
            340,
            listOf(ticket(1, "123456")),
            1000,
            PartialFailure(2, DhlotteryException("세트 부분완료 — 결과 불명분은 내역으로 대조하세요")),
        )
        val body = pensionAutoNotice(PensionOutcome.Purchased(result), isLastAttempt = false)!!.body
        assertFalse(body.contains("구매되지 않았습니다"))
        assertTrue(body.contains("결과를 확인하지 못했습니다"))
    }

    @Test
    fun `retryable failures stay silent until the last attempt`() {
        val login = PensionOutcome.LoginFailed(DhlotteryException("x"))
        assertNull(pensionAutoNotice(login, isLastAttempt = false))
        assertNotNull(pensionAutoNotice(login, isLastAttempt = true))
        assertNull(pensionAutoNotice(PensionOutcome.PreparationFailed, isLastAttempt = false))
        assertNotNull(pensionAutoNotice(PensionOutcome.PreparationFailed, isLastAttempt = true))
    }

    @Test
    fun `unknown result warns immediately and does not suggest a retry`() {
        val notice = pensionAutoNotice(PensionOutcome.ResultUnknown(DhlotteryException("x")), isLastAttempt = false)!!
        assertTrue(notice.body.contains("다시 시도하지 않았습니다"))
        assertEquals(notice, pensionAutoNotice(PensionOutcome.PendingUnresolved, isLastAttempt = false))
    }

    @Test
    fun `quiet outcomes produce no notification`() {
        assertNull(pensionAutoNotice(null, isLastAttempt = true))
        assertNull(pensionAutoNotice(PensionOutcome.AlreadyPurchased, isLastAttempt = true))
        assertNull(pensionAutoNotice(PensionOutcome.NotLoggedIn, isLastAttempt = true))
        assertNull(pensionAutoNotice(PensionOutcome.RoundChanged, isLastAttempt = true))
    }

    @Test
    fun `prize text follows the official table`() {
        assertEquals("월 700만원×20년", pensionPrizeText(Rank720.FIRST))
        assertEquals("월 100만원×10년", pensionPrizeText(Rank720.BONUS))
        assertEquals("₩1,000", pensionPrizeText(Rank720.SEVENTH))
        assertEquals("", pensionPrizeText(Rank720.NONE))
    }
}
