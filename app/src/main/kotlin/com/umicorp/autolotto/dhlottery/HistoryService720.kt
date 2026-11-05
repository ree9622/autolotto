package com.umicorp.autolotto.dhlottery

import com.umicorp.autolotto.data.Rank720
import com.umicorp.autolotto.data.RankChecker720
import com.umicorp.autolotto.data.Ticket720
import com.umicorp.autolotto.data.lumpPrize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 연금복권720+ 구매 내역 조회. 6/45와 달리 원장 목록 항목에 티켓 정보가 모두 담겨 상세 조회가 없다.
 * 원장 항목(`data.list[]`, `ltGdsCd == "LP72"`) 필드:
 *   gmInfo="조:번호[,조:번호…]" · ltEpsd(회차) · eltOrdrDt(구매일) · ltWnResult("미추첨"=추첨 전) ·
 *   wnRnk(1~7등, 8=보너스) · ltWnAmt(당첨금) — 뒤 둘은 **주문 단위 대표값**이라 게임별 등수는
 *   [resolveRanks]가 당첨번호로 다시 판정한다.
 *
 * 6/45 [HistoryService]와 같은 원칙: 세션 만료(HTML)·조회 실패는 빈 목록으로 뭉개지 않고 던진다.
 * 빈 목록은 "구매 없음"으로 읽히므로, 호출자가 실패와 구분할 수 있어야 한다.
 */
class HistoryService720(
    private val session: DhlotterySession,
    private val resultService: ResultService720,
) {

    /** 최근 구매 내역(최근 90일) — 결과 확인·결과 불명 대조용. */
    suspend fun fetchRecentPurchases(): List<Ticket720> {
        val today = LocalDate.now(Round720.KST)
        return fetchPurchases(today.minusDays(90), today)
    }

    /** [from]~[to](포함) 기간의 구매 내역. 동행복권 조회창 한도(최대 3개월)는 호출자가 지킨다. */
    suspend fun fetchPurchases(from: LocalDate, to: LocalDate): List<Ticket720> = withContext(Dispatchers.IO) {
        require(!from.isAfter(to)) { "조회 시작일은 종료일보다 늦을 수 없습니다." }
        session.get(session.base(ApiConstants.MYPAGE_LEDGER)).close()

        val tickets = mutableListOf<Ticket720>()
        val seenPages = mutableSetOf<String>()
        var page = 1
        while (true) {
            currentCoroutineContext().ensureActive()
            val listBody = session.get(
                session.base(ApiConstants.PURCHASE_HISTORY) +
                    "?srchStrDt=${DATE_FMT.format(from)}&srchEndDt=${DATE_FMT.format(to)}" +
                    "&pageNum=$page&recordCountPerPage=$PAGE_SIZE&_=${System.currentTimeMillis()}",
                mapOf(
                    "Accept" to "application/json, text/javascript, */*; q=0.01",
                    "X-Requested-With" to "XMLHttpRequest",
                    "Referer" to session.base(ApiConstants.MYPAGE_LEDGER),
                ),
            ).use {
                check(it.isSuccessful) { "구매 내역 목록 요청에 실패했습니다." }
                it.body?.string().orEmpty()
            }

            // HTML(세션 만료)이면 JSONObject가 던진다.
            val items = JSONObject(listBody).optJSONObject("data")?.optJSONArray("list")
            check(items != null) { "구매 내역 목록 응답이 누락되었습니다." }
            if (items.length() == 0) break
            check(seenPages.add(items.toString())) { "구매 내역 서버가 같은 페이지를 반복했습니다." }

            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                if (item.optString("ltGdsCd") == PRODUCT_CODE) tickets += parseOrder(item)
            }
            page++
        }
        resolveRanks(tickets)
    }

    private fun parseOrder(item: JSONObject): List<Ticket720> {
        val round = item.optString("ltEpsd").toIntOrNull()
            ?: item.optString("ltEpsdView").filter { it.isDigit() }.toIntOrNull()
            ?: return emptyList()
        val drawn = item.optString("ltWnResult") != "미추첨"
        val rank = if (drawn) rankOf(item.optString("wnRnk")) else Rank720.PENDING
        val purchaseDt = parseDate(item.optString("eltOrdrDt"))

        // gmInfo="조:번호[,조:번호…]" — 게임별 티켓으로 평탄화. 손상 항목은 건너뛴다.
        return item.optString("gmInfo").split(",").mapNotNull { pair ->
            val parts = pair.split(":")
            val jo = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return@mapNotNull null
            val number = parts.getOrNull(1)?.trim().orEmpty()
            if (jo !in 1..5 || !number.matches(SIX_DIGITS)) return@mapNotNull null
            Ticket720(
                round = round, jo = jo, number = number,
                rank = rank, prize = rank.lumpPrize, checked = drawn, purchaseDate = purchaseDt,
            )
        }
    }

    /**
     * 원장의 등수·당첨금은 주문 대표값이라 그대로 쓰면 주문 안 1게임만 당첨돼도 전 게임이 그 등수로
     * 표시된다. 당첨번호가 있는 회차는 게임별로 다시 판정하고, 당첨번호 조회 실패 시 원장 값을 유지한다.
     */
    private suspend fun resolveRanks(tickets: List<Ticket720>): List<Ticket720> {
        if (tickets.isEmpty()) return tickets
        val winning = resultService.getWinningNumbersMap(tickets.mapTo(mutableSetOf()) { it.round })
        return tickets.map { t ->
            val w = winning[t.round] ?: return@map t
            val r = RankChecker720.rankOf(t.jo, t.number, w)
            t.copy(rank = r, prize = r.lumpPrize, checked = true)
        }
    }

    private companion object {
        const val PRODUCT_CODE = "LP72"
        const val PAGE_SIZE = 100
        val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        val SIX_DIGITS = Regex("\\d{6}")

        /** wnRnk(1~7=1~7등, 8=보너스) → [Rank720]. 추첨됐으나 미당첨(null/0/범위밖) → NONE. */
        fun rankOf(wnRnk: String): Rank720 = when (wnRnk.trim()) {
            "1" -> Rank720.FIRST
            "2" -> Rank720.SECOND
            "3" -> Rank720.THIRD
            "4" -> Rank720.FOURTH
            "5" -> Rank720.FIFTH
            "6" -> Rank720.SIXTH
            "7" -> Rank720.SEVENTH
            "8" -> Rank720.BONUS
            else -> Rank720.NONE
        }

        /** yyyy-MM-dd / yyyyMMdd → 자정 LocalDateTime. 실패 시 now(KST). */
        fun parseDate(raw: String): LocalDateTime {
            runCatching { return LocalDate.parse(raw).atStartOfDay() }
            runCatching { return LocalDate.parse(raw, DATE_FMT).atStartOfDay() }
            return LocalDateTime.now(Round720.KST)
        }
    }
}
