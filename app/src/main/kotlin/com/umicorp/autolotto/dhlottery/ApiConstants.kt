package com.umicorp.autolotto.dhlottery

/**
 * 동행복권 API 상수. host(베이스 URL)는 [DhlotterySession]이 붙인다 —
 * 테스트에서 MockWebServer URL을 주입할 수 있도록 경로만 상수로 둔다.
 */
object ApiConstants {
    const val DEFAULT_BASE = "https://www.dhlottery.co.kr"
    const val DEFAULT_OLOTTO = "https://ol.dhlottery.co.kr"
    /** 연금복권720+ 구매 전용 도메인. 6/45(ol)와 달리 요청·응답을 AES로 암호화한다. */
    const val DEFAULT_EL = "https://el.dhlottery.co.kr"

    // www 경로
    const val LOGIN_PAGE = "/login"
    const val RSA_MODULUS = "/login/selectRsaModulus.do"
    const val LOGIN = "/login/securityLoginCheck.do"
    const val MAIN = "/main"
    const val BALANCE = "/mypage/selectUserMndp.do"
    const val MYPAGE_LEDGER = "/mypage/mylotteryledger" // 원장 페이지(.do 아님) — 내역 방문/Referer용
    const val PURCHASE_HISTORY = "/mypage/selectMyLotteryledger.do"
    const val TICKET_DETAIL = "/mypage/lotto645TicketDetail.do"
    const val WINNING_NUMBER = "/lt645/selectPstLt645Info.do"
    const val CHARGE_PAGE = "/mypage/mndpChrg"
    const val KBANK_INIT = "/mypage/kbankInit.do"
    const val KBANK_PROCESS = "/mypage/kbankProcess.do"

    // ol 경로
    const val GAME645 = "/olotto/game/game645.do"
    const val READY_SOCKET = "/olotto/game/egovUserReadySocket.json"
    const val EXEC_BUY = "/olotto/game/execBuy.do"

    // 연금복권720+ — 당첨번호(www, 로그인 불필요)
    const val WINNING_NUMBER_720 = "/pt720/selectPstPt720WnList.do"

    // 연금복권720+ — 구매(el). makeOrderNo까지 무결제, connPro가 실결제.
    const val TOTAL_GAME_720 = "/game/TotalGame.jsp?LottoId=LP72"
    const val GAME720 = "/game/pension720/game.jsp"
    const val MAKE_AUTO_NO_720 = "/makeAutoNo.do"
    const val CHECK_VERIFY_NO_720 = "/checkVerifyNo.do"
    const val MAKE_ORDER_NO_720 = "/makeOrderNo.do"
    const val CONN_PRO_720 = "/connPro.do"

    val DEFAULT_HEADERS: Map<String, String> = mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language" to "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7",
        "Connection" to "keep-alive",
    )
}
