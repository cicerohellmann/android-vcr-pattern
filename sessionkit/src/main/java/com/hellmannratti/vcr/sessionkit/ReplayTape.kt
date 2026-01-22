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
    private val map: Map<RequestKey, List<RecordedResponse>>,
    private val patterns: List<UrlPattern> = emptyList()
) {
    val uniqueRequestCount: Int get() = map.size

    private val cursor = mutableMapOf<RequestKey, Int>()

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
        fun nextFor(k: RequestKey): RecordedResponse? {
            val list = map[k] ?: return null
            val i = cursor[k] ?: 0
            if (i !in list.indices) return null
            cursor[k] = i + 1
            return list[i]
        }

        val k1 = key(request, bodySha256)
        val k2 = if (bodySha256 != null) key(request, null) else null

        return nextFor(k1) ?: (k2?.let(::nextFor))
    }

    fun resetCursors() {
        cursor.clear()
    }

    fun entries(): Sequence<Triple<RequestKey, Int, RecordedResponse>> = sequence {
        for ((k, list) in map) {
            for (i in list.indices) {
                yield(Triple(k, i, list[i]))
            }
        }
    }
}
