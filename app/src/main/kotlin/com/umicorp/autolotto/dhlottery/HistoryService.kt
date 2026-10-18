package com.umicorp.autolotto.dhlottery

import com.umicorp.autolotto.data.Purchase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 동행복권 마이페이지 구매 내역 조회 (Flutter HistoryService 포트).
 *
 * 내역은 로컬 DB 없이 매번 라이브 조회한다(DESIGN §3). 머니패스는 아니지만 등수/당첨금 표시의
 * 출처이므로 원본 history_service.dart와 의미상 1:1을 유지한다.
 *
 * 흐름: 마이페이지 원장 방문(Referer용) → selectMyLotteryledger.do(목록) →
 *       "로또6/45" 항목마다 lotto645TicketDetail.do(번호+당첨결과) → Purchase 빌드.
 *
 * 세션 만료 시 JSON 자리에 HTML이 오면 파싱 예외가 발생한다.
 * 파싱 실패와 상세 조회 실패는 전파하여 일부 내역을 전체 집계로 오인하지 않게 한다.
 *
 * 세션(로그인 쿠키 보유)은 생성자 주입. 원본은 `_auth.dio`(리다이렉트 추적 off)를 썼으므로 follow는 기본값(false).
 */
class HistoryService(private val session: DhlotterySession) {

    /** 최근 구매 내역 (최근 30일, 최대 [count]건) — 결과확인 워커용. */
    suspend fun fetchRecentPurchases(count: Int = 5): List<Purchase> =
        fetchPurchases(LocalDate.now().minusDays(30), LocalDate.now(), count)

    /**
     * [from]~[to](포함) 기간의 구매 내역. 동행복권 조회창 한도(최대 3개월)는 호출자가 지킨다 —
     * 각 기간창의 마지막 페이지까지 조회하며 [count]가 지정되면 해당 건수에서 멈춘다.
     */
    suspend fun fetchPurchases(from: LocalDate, to: LocalDate, count: Int = Int.MAX_VALUE): List<Purchase> = withContext(Dispatchers.IO) {
        require(!from.isAfter(to)) { "조회 시작일은 종료일보다 늦을 수 없습니다." }
        require(count >= 0) { "조회 건수는 음수일 수 없습니다." }
        if (count == 0) return@withContext emptyList()
        currentCoroutineContext().ensureActive()
        val now = LocalDateTime.now() // 날짜 파싱 실패 시 폴백(원본 동작)
        val todayStr = DATE_FMT.format(to)
        val fromStr = DATE_FMT.format(from)

        // 1. 마이페이지 원장 방문 (Referer용)
        session.get(session.base(ApiConstants.MYPAGE_LEDGER)).close()

        val purchases = mutableListOf<Purchase>()
        val seenPages = mutableSetOf<String>()
        val seenOrders = mutableSetOf<Pair<String, String>>()
        var page = 1
        while (true) {
            currentCoroutineContext().ensureActive()
            // 2. 구매 목록 조회
            val listBody = session.get(
                session.base(ApiConstants.PURCHASE_HISTORY) +
                    "?srchStrDt=$fromStr&srchEndDt=$todayStr&pageNum=$page&recordCountPerPage=$PAGE_SIZE&_=${System.currentTimeMillis()}",
                mapOf(
                    "Accept" to "application/json, text/javascript, */*; q=0.01",
                    "X-Requested-With" to "XMLHttpRequest",
                    "Referer" to session.base(ApiConstants.MYPAGE_LEDGER),
                ),
            ).use {
                check(it.isSuccessful) { "구매 내역 목록 요청에 실패했습니다." }
                it.body?.string().orEmpty()
            }

            // HTML(세션 만료)이면 JSONObject가 던진다 → 원본 Dart의 String 인덱싱 throw와 동일하게 전파.
            val items = JSONObject(listBody).optJSONObject("data")?.optJSONArray("list")
            check(items != null) { "구매 내역 목록 응답이 누락되었습니다." }
            if (items.length() == 0) break
            check(seenPages.add(items.toString())) { "구매 내역 서버가 같은 페이지를 반복했습니다." }

            for (i in 0 until items.length()) {
                currentCoroutineContext().ensureActive()
                val item = items.getJSONObject(i)
                if (item.optString("ltGdsNm") != "로또6/45") continue

                val round = item.optString("ltEpsdView").replace("회", "").trim().toIntOrNull() ?: 0
                val ntslOrdrNo = item.optString("ntslOrdrNo")
                val gmInfo = item.optString("gmInfo")
                val purchaseDateStr = item.optString("eltOrdrDt")
                val drawDateStr = item.optString("epsdRflDt")
                check(ntslOrdrNo.isNotEmpty() && gmInfo.isNotEmpty()) { "구매 내역의 주문 정보가 누락되었습니다." }
                check(seenOrders.add(ntslOrdrNo to gmInfo)) { "구매 내역 페이지에 중복 주문이 있습니다." }

                // 3. 상세 조회 (번호 + 당첨결과)
                run {
                    val purchaseDt = parseDhDateTime(purchaseDateStr) ?: now
                    val startDt = DATE_FMT.format(purchaseDt.minusDays(7))
                    val endDt = DATE_FMT.format(purchaseDt.plusDays(7))

                    val detailBody = session.get(
                        session.base(ApiConstants.TICKET_DETAIL) +
                            "?ntslOrdrNo=$ntslOrdrNo&srchStrDt=$startDt&srchEndDt=$endDt&barcd=$gmInfo",
                        mapOf(
                            "X-Requested-With" to "XMLHttpRequest",
                            "Referer" to session.base(ApiConstants.MYPAGE_LEDGER),
                        ),
                    ).use {
                        check(it.isSuccessful) { "구매 내역 상세 요청에 실패했습니다." }
                        it.body?.string().orEmpty()
                    }

                    val data = JSONObject(detailBody).optJSONObject("data")
                    check(data != null && data.optBoolean("success", false)) { "구매 내역 상세를 불러오지 못했습니다." }

                    val ticket = data.getJSONObject("ticket")
                    val gameDtl = ticket.optJSONArray("game_dtl") ?: JSONArray()
                    val drawed = ticket.optBoolean("drawed", false)

                    // 당첨번호 추출 (win_num 없으면 null — 원본의 비저장 필드).
                    // 실측 정규화(에뮬레이터 1233/1234회): 미추첨이면 win_num이 0으로 채워져 오고,
                    // 추첨 후엔 보너스 포함 7개에 bonus_num이 비기도 한다 → 미추첨=null, 7개면 7번째=보너스.
                    val winNumRaw = ticket.optJSONArray("win_num")?.let { toIntList(it) }
                        ?.takeIf { drawed && it.all { n -> n in 1..45 } }
                    val winNum = winNumRaw?.take(6)
                    val bonusNum = ticket.optIntOrNull("bonus_num")?.takeIf { it in 1..45 }
                        ?: winNumRaw?.getOrNull(6)

                    val numbers = mutableListOf<List<Int>>()
                    var autoCount = 0
                    var manualCount = 0
                    // 게임별 당첨결과 (API가 직접 제공)
                    val gameRanks = mutableListOf<String>()
                    val gamePrizes = mutableListOf<Long>()

                    for (g in 0 until gameDtl.length()) {
                        val game = gameDtl.getJSONObject(g)
                        val nums = game.optJSONArray("num")?.let { toIntList(it) } ?: emptyList()
                        check(nums.size == 6 && nums.all { it in 1..45 }) { "구매 내역 상세의 번호가 올바르지 않습니다." }
                        run {
                            numbers.add(nums)
                            val type = game.optInt("type", 3)
                            if (type == 1) manualCount++ else autoCount++

                            val apiRank = game.optInt("rank", 0)
                            val apiAmt = game.optLong("amt", 0)
                            gameRanks.add(if (drawed) rankLabel(apiRank) else "pending")
                            gamePrizes.add(apiAmt)
                        }
                    }

                    check(numbers.isNotEmpty()) { "구매 내역 상세에 번호가 없습니다." }

                    // 추첨 완료 여부로 checked 판단
                    val checked = drawed
                    var bestRank: String? = null
                    var totalPrize = 0L
                    if (checked) {
                        bestRank = "nowin"
                        for (r in gameRanks) {
                            if (RANK_ORDER.indexOf(r) < RANK_ORDER.indexOf(bestRank!!)) bestRank = r
                        }
                        totalPrize = gamePrizes.sum()
                    }

                    purchases.add(
                        Purchase(
                            round = round,
                            date = parseDhDateTime(drawDateStr) ?: parseDhDateTime(purchaseDateStr) ?: now,
                            numbers = numbers,
                            autoCount = autoCount,
                            manualCount = manualCount,
                            amount = numbers.size * 1000,
                            checked = checked,
                            rank = bestRank,
                            prize = totalPrize,
                            gameRanks = gameRanks,
                            gamePrizes = gamePrizes,
                            winningNumbers = winNum,
                            bonusNumber = bonusNum,
                        ),
                    )
                    if (purchases.size >= count) return@withContext purchases
                }
            }
            page++
        }

        purchases
    }

    private companion object {
        const val PAGE_SIZE = 100
        val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val COMPACT_DT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

        // bestRank 비교 순서: 작은 인덱스가 더 좋은 등수 (원본 rankOrder 그대로).
        val RANK_ORDER = listOf("rank1", "rank2", "rank3", "rank4", "rank5", "nowin")

        /** API rank 숫자 → 코드값 (1~5=rank1~rank5, 그 외=nowin). 원본 _rankLabel. */
        fun rankLabel(rank: Int): String = if (rank in 1..5) "rank$rank" else "nowin"

        /**
         * Dart DateTime.tryParse 대응(실패 시 null). dhlottery가 쓰는 형식만 처리:
         * ISO('T') · 공백 구분 datetime · 날짜만(yyyy-MM-dd).
         * ponytail: 라이브에서 다른 형식이 나오면 패턴 추가 — 실패해도 원본처럼 now로 폴백한다.
         */
        fun parseDhDateTime(raw: String): LocalDateTime? {
            if (raw.isBlank()) return null
            runCatching { return LocalDateTime.parse(raw) }
            runCatching { return LocalDateTime.parse(raw.replace(' ', 'T')) }
            runCatching { return LocalDate.parse(raw).atStartOfDay() }                                   // yyyy-MM-dd
            runCatching { return LocalDate.parse(raw, DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay() } // yyyyMMdd (Dart tryParse 허용)
            runCatching { return LocalDateTime.parse(raw, COMPACT_DT) }                                  // yyyyMMdd'T'HHmmss
            return null
        }
    }
}

/** `as int?`(없음/null → null) 대응. */
private fun JSONObject.optIntOrNull(key: String): Int? =
    if (has(key) && !isNull(key)) optInt(key) else null

/** JSONArray<int> → List<Int>. 원본 `.cast<int>()` 대응(단 즉시 변환). */
private fun toIntList(arr: JSONArray): List<Int> = (0 until arr.length()).map { arr.getInt(it) }
