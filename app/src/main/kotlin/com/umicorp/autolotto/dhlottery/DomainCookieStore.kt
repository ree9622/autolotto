package com.umicorp.autolotto.dhlottery

import okhttp3.Cookie
import okhttp3.HttpUrl

/**
 * 세션 쿠키 저장소. 부모 Domain 쿠키의 www ↔ ol 전파와 호스트 전용 쿠키의 격리를 유지한다.
 * 응답 호스트에 유효한 쿠키만 저장하며 Secure 쿠키는 HTTPS 요청에만 전달한다.
 * 기존 동작 보존을 위해 경로·만료에 따른 전송 정책은 여기서 추가하지 않는다.
 */
class DomainCookieStore {
    private val domainCookies = LinkedHashMap<String, LinkedHashMap<String, Cookie>>()

    @Synchronized
    fun clear() {
        domainCookies.clear()
    }

    /** 호스트만 아는 기존 호출자는 HTTPS로 취급한다. 실제 요청은 URL 경로를 사용한다. */
    fun cookieHeader(host: String): String = cookieHeader(
        HttpUrl.Builder().scheme("https").host(host).build(),
    )

    /** 요청 URL에 보낼 Cookie 헤더. Secure 속성을 전송 시점에 확인한다. */
    @Synchronized
    fun cookieHeader(url: HttpUrl): String {
        val merged = LinkedHashMap<String, Cookie>()
        for ((domain, cookies) in domainCookies) {
            if (domain.startsWith(".")) {
                val suffix = domain.substring(1)
                if (url.host == suffix || url.host.endsWith(".$suffix")) {
                    merged.putAll(cookies.filterValues { !it.secure || url.isHttps })
                }
            }
        }
        domainCookies[url.host]?.let { cookies ->
            merged.putAll(cookies.filterValues { !it.secure || url.isHttps })
        }
        return merged.values.joinToString("; ") { "${it.name}=${it.value}" }
    }

    /** Cookie.parse가 응답 호스트와 Domain의 관계 및 쿠키 구문을 검증한다. */
    @Synchronized
    fun store(setCookieHeaders: List<String>, requestHost: String) {
        val origin = HttpUrl.Builder().scheme("https").host(requestHost).build()
        for (header in setCookieHeaders) {
            val cookie = Cookie.parse(origin, header) ?: continue
            val domain = if (cookie.hostOnly) cookie.domain else ".${cookie.domain}"
            domainCookies.getOrPut(domain) { LinkedHashMap() }[cookie.name] = cookie
        }
    }

    @Synchronized
    fun hasCookie(name: String): Boolean = domainCookies.values.any { it.containsKey(name) }

    /** Cookie는 불변 객체이므로 중첩 맵 복사로 속성까지 보존한다. */
    @Synchronized
    fun snapshot(): Map<String, Map<String, Cookie>> = domainCookies.mapValues { LinkedHashMap(it.value) }

    @Synchronized
    fun restore(snapshot: Map<String, Map<String, Cookie>>) {
        domainCookies.clear()
        for ((domain, cookies) in snapshot) domainCookies[domain] = LinkedHashMap(cookies)
    }

    @Synchronized
    fun keysForDomain(domain: String): List<String> =
        domainCookies[domain]?.keys?.toList() ?: emptyList()
}
