package com.umicorp.autolotto.dhlottery

import com.umicorp.autolotto.data.DepositAccount
import com.umicorp.autolotto.data.DepositResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 동행복권 케이뱅크 가상계좌 예치금 충전 서비스.
 *
 * 동행복권의 2단계 케이뱅크 가상계좌 발급 프로세스를 처리합니다:
 * 1) [ApiConstants.KBANK_INIT] : 가상계좌 발급 초기화 요청 (reqVO 획득)
 * 2) [ApiConstants.KBANK_PROCESS] : 실제 전용 가상계좌 발급 승인 처리 (resVO 획득)
 */
class DepositService(private val session: DhlotterySession) {

    /**
     * [amount] 금액에 대한 케이뱅크 가상계좌 발급을 신청합니다.
     * 최소 충전 금액은 5,000원입니다.
     */
    suspend fun requestVirtualAccount(amount: Int): DepositResult = withContext(Dispatchers.IO) {
        if (amount < MIN_DEPOSIT_AMOUNT) {
            return@withContext DepositResult.Failure("최소 충전 금액은 5,000원입니다.")
        }

        try {
            // KST 기준 내일 날짜 계산 (YYYYMMDD)
            val kstZone = ZoneId.of("Asia/Seoul")
            val tomorrow = LocalDate.now(kstZone).plusDays(1)
            val vbankExpDate = tomorrow.format(EXP_DATE_FORMATTER)

            // Referer용 헤더
            val headers = mapOf(
                "Referer" to session.base(ApiConstants.CHARGE_PAGE),
                "X-Requested-With" to "XMLHttpRequest",
                "Accept" to "application/json, text/javascript, */*; q=0.01",
            )

            // Step 1: kbankInit.do 호출
            val initQuery = buildString {
                append("?VbankExpDate=").append(vbankExpDate)
                append("&PayMethod=VBANK")
                append("&VbankBankCode=089")
                append("&Price=").append(amount)
                append("&_=").append(System.currentTimeMillis())
            }

            val initUrl = session.base(ApiConstants.KBANK_INIT) + initQuery
            val initResponse = session.get(initUrl, headers)
            val initBody = initResponse.body?.string() ?: ""

            if (!initResponse.isSuccessful || initBody.isBlank()) {
                return@withContext DepositResult.Failure("가상계좌 발급 초기화 실패 (HTTP ${initResponse.code})")
            }

            val initJson = JSONObject(initBody)
            val dataObj = initJson.optJSONObject("data")
                ?: return@withContext DepositResult.Failure("서버 응답 오류 (초기화 데이터 누락)")

            val reqVo = dataObj.optJSONObject("reqVO")
                ?: return@withContext DepositResult.Failure("가상계좌 발급 요청 정보(reqVO)를 받지 못했습니다.")

            // Step 2: kbankProcess.do 호출
            val payMethod = reqVo.optString("payMethod", "VBANK")
            val goodsName = reqVo.optString("goodsName", "예치금충전")
            val moid = reqVo.optString("moid", "")
            val userIp = reqVo.optString("userIP", "")
            val mallUserId = reqVo.optString("mallUserID", "")
            val expDate = reqVo.optString("vbankExpDate", vbankExpDate)
            val amt = reqVo.optString("amt", amount.toString())
            val vbankBankCode = reqVo.optString("vbankBankCode", "089")
            val fxVrAccountNo = reqVo.optString("fxVrAccountNo", "")
            val buyerName = reqVo.optString("buyerName", "")

            val processQuery = buildString {
                append("?PayMethod=").append(urlEncode(payMethod))
                append("&GoodsName=").append(urlEncode(goodsName))
                append("&Moid=").append(urlEncode(moid))
                append("&UserIP=").append(urlEncode(userIp))
                append("&MallUserID=").append(urlEncode(mallUserId))
                append("&VbankExpDate=").append(urlEncode(expDate))
                append("&Amt=").append(urlEncode(amt))
                append("&VbankBankCode=").append(urlEncode(vbankBankCode))
                append("&VbankNum=").append(urlEncode(fxVrAccountNo))
                append("&FxVrAccountNo=").append(urlEncode(fxVrAccountNo))
                append("&VBankAccountName=").append(urlEncode(buyerName))
                append("&_=").append(System.currentTimeMillis())
            }

            val processUrl = session.base(ApiConstants.KBANK_PROCESS) + processQuery
            val processResponse = session.get(processUrl, headers)
            val processBody = processResponse.body?.string() ?: ""

            if (!processResponse.isSuccessful || processBody.isBlank()) {
                return@withContext DepositResult.Failure("가상계좌 승인 처리 실패 (HTTP ${processResponse.code})")
            }

            val processJson = JSONObject(processBody)
            val processData = processJson.optJSONObject("data")
                ?: return@withContext DepositResult.Failure("서버 응답 오류 (승인 데이터 누락)")

            val resVo = processData.optJSONObject("resVO")
                ?: return@withContext DepositResult.Failure("가상계좌 처리 결과(resVO)를 받지 못했습니다.")

            val resultCode = resVo.optString("resultCode", "")
            if (resultCode.equals("FAIL", ignoreCase = true)) {
                val errorMsg = resVo.optString("resultMsg", "가상계좌 발급이 거절되었습니다.")
                return@withContext DepositResult.Failure(errorMsg)
            }

            val vbankNum = resVo.optString("vbankNum", "").ifBlank {
                resVo.optString("fxVrAccountNo", "")
            }

            if (vbankNum.isBlank()) {
                return@withContext DepositResult.Failure("가상계좌 번호를 받지 못했습니다.")
            }

            val bankName = resVo.optString("vbankBankName", "케이뱅크").ifBlank { "케이뱅크" }
            val resolvedBuyer = resVo.optString("buyerName", buyerName).ifBlank {
                resVo.optString("vbankAccountName", buyerName)
            }
            val formatted = formatAccountNumber(vbankNum)
            val formattedExpDate = formatExpirationDate(expDate)

            DepositResult.Success(
                DepositAccount(
                    bankName = bankName,
                    accountNumber = vbankNum,
                    formattedAccount = formatted,
                    buyerName = resolvedBuyer,
                    amount = amount,
                    expirationDate = formattedExpDate,
                )
            )
        } catch (e: Exception) {
            DepositResult.Failure(e.localizedMessage ?: "가상계좌 발급 중 오류가 발생했습니다.")
        }
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, "UTF-8")

    companion object {
        const val MIN_DEPOSIT_AMOUNT = 5_000
        val ALLOWED_PRESET_AMOUNTS = listOf(5_000, 10_000, 20_000, 30_000, 50_000, 100_000)

        private val EXP_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd")

        /**
         * 가상계좌 번호를 읽기 쉬운 형태(예: 089-XXXX-XXX-XXXXXX)로 포맷팅합니다.
         */
        fun formatAccountNumber(raw: String): String {
            val digits = raw.filter { it.isDigit() }
            return when {
                digits.length >= 13 -> "${digits.substring(0, 3)}-${digits.substring(3, 7)}-${digits.substring(7, 10)}-${digits.substring(10)}"
                digits.length >= 11 -> "${digits.substring(0, 3)}-${digits.substring(3, 7)}-${digits.substring(7)}"
                digits.length >= 7 -> "${digits.substring(0, 3)}-${digits.substring(3)}"
                else -> digits
            }
        }

        /**
         * 만료 일자(YYYYMMDD 또는 YYYYMMDDHHmmss)를 읽기 쉬운 문자열로 변환합니다.
         */
        fun formatExpirationDate(raw: String): String {
            val digits = raw.filter { it.isDigit() }
            return if (digits.length >= 8) {
                val y = digits.substring(0, 4)
                val m = digits.substring(4, 6)
                val d = digits.substring(6, 8)
                "${y}년 ${m}월 ${d}일 23:59:59"
            } else {
                raw
            }
        }
    }
}
