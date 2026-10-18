package com.umicorp.autolotto.data

/**
 * 동행복권 케이뱅크 가상계좌 충전 정보.
 */
data class DepositAccount(
    val bankName: String = "케이뱅크",
    val accountNumber: String,
    val formattedAccount: String,
    val buyerName: String,
    val amount: Int,
    val expirationDate: String,
)

/**
 * 예치금 충전 결과.
 */
sealed interface DepositResult {
    data class Success(val account: DepositAccount) : DepositResult
    data class Failure(val message: String) : DepositResult
}
