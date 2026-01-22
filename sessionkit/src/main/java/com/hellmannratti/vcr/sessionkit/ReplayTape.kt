package com.hellmannratti.vcr.sessionkit

import okhttp3.HttpUrl
import okhttp3.Request

internal data class RequestKey(val method: String, val url: String, val bodySha256: String?)

internal data class RecordedResponse(
    val code: Int,
    val headers: Map<String, String>,
    val body: String,
    val durationMs: Long
)

internal class ReplayTape(
    private val map: MutableMap<RequestKey, ArrayDeque<RecordedResponse>>,
    private val patterns: List<UrlPattern> = emptyList()
) {
    val uniqueRequestCount: Int get() = map.size

    private fun canonicalUrl(url: HttpUrl): String = url.toString()

    private fun normalizeUrl(url: String): String {
        for (pattern in patterns) {
            if (pattern.matches(url)) {
                return pattern.normalize(url)
            }
        }
        return url
    }

    private fun key(request: Request, bodySha256: String?): RequestKey {
        val url = canonicalUrl(request.url)
        val normalizedUrl = normalizeUrl(url)
        return RequestKey(request.method, normalizedUrl, bodySha256)
    }

    fun find(request: Request, bodySha256: String?): RecordedResponse? {
        map[key(request, bodySha256)]?.removeFirstOrNull()?.let { return it }
        if (bodySha256 != null) {
            map[key(request, null)]?.removeFirstOrNull()?.let { return it }
        }
        return null
    }

    fun entries(): Sequence<Triple<RequestKey, Int, RecordedResponse>> = sequence {
        for ((k, q) in map) {
            var i = 0
            for (r in q) {
                yield(Triple(k, i, r))
                i++
            }
        }
    }
}
