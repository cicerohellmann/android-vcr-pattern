package com.hellmannratti.zombie.replay

import okhttp3.HttpUrl
import okhttp3.Request

/**
 * Tape building and lookup structures.
 */
data class RequestKey(val method: String, val url: String, val bodySha256: String?)


data class RecordedResponse(
    val code: Int,
    val headers: Map<String, String>,
    val body: String,
    val durationMs: Long
)

class ReplayTape(
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

    /**
     * Lookup with fallback: exact (method,url,hash) first; if not found and hash != null, try (method,url,null).
     * When found, pops the first queued response to preserve call ordering.
     */
    fun find(request: Request, bodySha256: String?): RecordedResponse? {
        map[key(request, bodySha256)]?.removeFirstOrNull()?.let { return it }
        if (bodySha256 != null) {
            map[key(request, null)]?.removeFirstOrNull()?.let { return it }
        }
        return null
    }

    /**
     * Read-only snapshot view used for saving/inspection. Does NOT mutate the tape.
     * Emits triples of (RequestKey, indexInQueue, RecordedResponse).
     */
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
