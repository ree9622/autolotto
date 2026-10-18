package com.umicorp.autolotto.data

/** 불러온 구매 내역만 집계하며, 금액은 원 단위다. */
data class HistorySummary(
    val totalPurchaseAmount: Long,
    val totalPrizeAmount: Long,
    val netAmount: Long,
    val winningRoundCount: Int,
    val pendingRoundCount: Int,
)

/** 미추첨 또는 미확인 내역을 당첨으로 표시하지 않는다. */
val Purchase.isWinning: Boolean
    get() = checked && (rank in WINNING_RANKS || prize > 0L || gameRanks.orEmpty().any { it in WINNING_RANKS })

/** 같은 회차에 주문이 여러 번 있어도 회차 수는 한 번만 센다. */
fun summarizeHistory(purchases: List<Purchase>): HistorySummary {
    val totalPurchase = purchases.sumOf { it.amount.toLong() }
    val totalPrize = purchases.filter { it.checked }.sumOf { it.prize }
    return HistorySummary(
        totalPurchaseAmount = totalPurchase,
        totalPrizeAmount = totalPrize,
        netAmount = totalPrize - totalPurchase,
        winningRoundCount = purchases.filter { it.isWinning }.map { it.round }.distinct().size,
        pendingRoundCount = purchases.filter { !it.checked }.map { it.round }.distinct().size,
    )
}

private val WINNING_RANKS = setOf("rank1", "rank2", "rank3", "rank4", "rank5")
