package com.umicorp.autolotto.dhlottery

import com.umicorp.autolotto.data.DepositResult
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * DepositService 단위 테스트 — MockWebServer로 kbankInit.do와 kbankProcess.do를 검증.
 */
class DepositServiceTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun ok(body: String) = MockResponse().setResponseCode(200).setBody(body)

    @Test
    fun `정상적인 케이뱅크 가상계좌 발급 성공 검증`() = runBlocking {
        val initJson = """
            {
              "data": {
                "reqVO": {
                  "payMethod": "VBANK",
                  "goodsName": "예치금충전",
                  "moid": "MOID12345",
                  "userIP": "127.0.0.1",
                  "mallUserID": "testuser",
                  "vbankExpDate": "20261020",
                  "amt": "10000",
                  "vbankBankCode": "089",
                  "fxVrAccountNo": "08912345678901",
                  "buyerName": "홍길동"
                }
              }
            }
        """.trimIndent()

        val processJson = """
            {
              "data": {
                "resVO": {
                  "resultCode": "SUCCESS",
                  "resultMsg": "정상처리",
                  "vbankNum": "08912345678901",
                  "vbankBankName": "케이뱅크",
                  "buyerName": "홍길동",
                  "amt": "10000",
                  "vbankExpDate": "20261020"
                }
              }
            }
        """.trimIndent()

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path?.substringBefore('?')
                return when (path) {
                    ApiConstants.KBANK_INIT -> ok(initJson)
                    ApiConstants.KBANK_PROCESS -> ok(processJson)
                    else -> ok("")
                }
            }
        }

        val baseUrl = server.url("/").toString().trimEnd('/')
        val service = DepositService(DhlotterySession(baseUrl = baseUrl, olottoUrl = baseUrl))

        val result = service.requestVirtualAccount(10_000)

        assertTrue("결과는 Success여야 함", result is DepositResult.Success)
        val success = result as DepositResult.Success
        assertEquals("케이뱅크", success.account.bankName)
        assertEquals("08912345678901", success.account.accountNumber)
        assertEquals("089-1234-567-8901", success.account.formattedAccount)
        assertEquals("홍길동", success.account.buyerName)
        assertEquals(10_000, success.account.amount)
        assertTrue(success.account.expirationDate.contains("2026년 10월 20일"))
    }

    @Test
    fun `최소 금액 미만 요청 시 즉시 Failure 반환`() = runBlocking {
        val baseUrl = server.url("/").toString().trimEnd('/')
        val service = DepositService(DhlotterySession(baseUrl = baseUrl, olottoUrl = baseUrl))

        val result = service.requestVirtualAccount(3_000)

        assertTrue("3000원은 최소 금액 미만이어야 함", result is DepositResult.Failure)
        val failure = result as DepositResult.Failure
        assertTrue(failure.message.contains("최소 충전 금액"))
    }

    @Test
    fun `서버에서 거절(FAIL) 응답 시 Failure 반환`() = runBlocking {
        val initJson = """
            {
              "data": {
                "reqVO": {
                  "payMethod": "VBANK",
                  "goodsName": "예치금충전",
                  "moid": "MOID12345",
                  "amt": "50000",
                  "fxVrAccountNo": "08912345678901"
                }
              }
            }
        """.trimIndent()

        val processFailJson = """
            {
              "data": {
                "resVO": {
                  "resultCode": "FAIL",
                  "resultMsg": "일일 충전 한도 초과"
                }
              }
            }
        """.trimIndent()

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path?.substringBefore('?')
                return when (path) {
                    ApiConstants.KBANK_INIT -> ok(initJson)
                    ApiConstants.KBANK_PROCESS -> ok(processFailJson)
                    else -> ok("")
                }
            }
        }

        val baseUrl = server.url("/").toString().trimEnd('/')
        val service = DepositService(DhlotterySession(baseUrl = baseUrl, olottoUrl = baseUrl))

        val result = service.requestVirtualAccount(50_000)

        assertTrue("FAIL 응답 시 Failure여야 함", result is DepositResult.Failure)
        val failure = result as DepositResult.Failure
        assertEquals("일일 충전 한도 초과", failure.message)
    }

    @Test
    fun `계좌번호 및 만료일자 포맷팅 검증`() {
        assertEquals("089-1234-567-8901", DepositService.formatAccountNumber("08912345678901"))
        assertEquals("089-1234-5678", DepositService.formatAccountNumber("08912345678"))
        assertEquals("2026년 10월 25일 23:59:59", DepositService.formatExpirationDate("20261025"))
    }
}
