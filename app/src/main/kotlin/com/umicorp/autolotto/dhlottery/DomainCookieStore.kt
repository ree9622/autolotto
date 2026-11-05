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

    // 고정된 쿠키는 Set-Cookie로 바뀌지 않는다. 연금복권 구매 중 서버가 JSESSIONID를 회전시켜도
    // 주문(makeOrderNo)과 결제(connPro)가 같은 세션에 묶이게 한다.
    private val frozen = HashSet<String>()

    @Synchronized
    fun freeze(name: String) { frozen.add(name) }

    @Synchronized
    fun unfreeze(name: String) { frozen.remove(name) }

    @Synchronized
    fun clear() {
        domainCookies.clear()
        frozen.clear()
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
            if (cookie.name in frozen) continue
            val domain = if (cookie.hostOnly) cookie.domain else ".${cookie.domain}"
            domainCookies.getOrPut(domain) { LinkedHashMap() }[cookie.name] = cookie
        }
    }

    /**
     * [url]로 전송될 쿠키 중 [name]의 값. 연금복권 암호화 키(el JSESSIONID) 조회용으로,
     * www와 el의 JSESSIONID가 다를 수 있어 전송 규칙([cookieHeader])과 같은 우선순위로 찾는다.
     */
    @Synchronized
    fun cookieValue(url: HttpUrl, name: String): String? {
        var value: String? = null
        for ((domain, cookies) in domainCookies) {
            if (domain.startsWith(".")) {
                val suffix = domain.substring(1)
                if (url.host == suffix || url.host.endsWith(".$suffix")) {
                    cookies[name]?.takeIf { !it.secure || url.isHttps }?.let { value = it.value }
                }
            }
        }
        domainCookies[url.host]?.get(name)?.takeIf { !it.secure || url.isHttps }?.let { value = it.value }
        return value
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
