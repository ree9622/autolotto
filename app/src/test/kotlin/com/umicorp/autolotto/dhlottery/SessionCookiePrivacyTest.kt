package com.umicorp.autolotto.dhlottery

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/** Exercise the request interceptor on a local server; never contact the real service. */
class SessionCookiePrivacyTest {
    private lateinit var server: MockWebServer
    private lateinit var session: DhlotterySession

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        val base = server.url("/").toString().trimEnd('/')
        session = DhlotterySession(baseUrl = base, olottoUrl = base)
    }

    @After fun tearDown() = server.shutdown()

    @Test fun secureCookieIsNotForwardedOnAnHttpRequest() {
        server.enqueue(MockResponse()
            .addHeader("Set-Cookie", "SECRET=private; Secure; Path=/")
            .addHeader("Set-Cookie", "PLAIN=public; Path=/"))
        server.enqueue(MockResponse())

        session.get(session.base("/set")).close()
        session.get(session.base("/check")).close()
        server.takeRequest()
        assertEquals("PLAIN=public", server.takeRequest().getHeader("Cookie"))
    }

    @Test fun responseCannotPlantACookieOnAnUnrelatedHost() {
        server.enqueue(MockResponse().addHeader("Set-Cookie", "INJECTED=value; Domain=example.com; Path=/"))
        session.get(session.base("/set")).close()

        assertFalse(session.cookies.hasCookie("INJECTED"))
        assertEquals("", session.cookies.cookieHeader("example.com"))
    }
}
