package com.hellmannratti.cassete.core

data class RequestKey(val method: String, val url: String, val bodySha256: String?)

data class RecordedResponse(
    val code: Int,
    val headers: Map<String, String>,
    val body: String,
    val durationMs: Long
) {
    fun toSnapshot(): HttpResponseSnapshot = HttpResponseSnapshot(
        code = code,
        headers = headers,
        bodyUtf8 = body,
        durationMs = durationMs
    )

    companion object {
        fun fromSnapshot(snapshot: HttpResponseSnapshot): RecordedResponse = RecordedResponse(
            code = snapshot.code,
            headers = snapshot.headers,
            body = snapshot.bodyUtf8,
            durationMs = snapshot.durationMs
        )
    }
}

class ReplayTape(
    private val map: Map<RequestKey, List<RecordedResponse>>,
    private val normalizers: List<UrlNormalizer> = emptyList()
) {
    val uniqueRequestCount: Int get() = map.size

    private val cursor = mutableMapOf<RequestKey, Int>()

    fun find(request: HttpRequestSnapshot): RecordedResponse? {
        fun nextFor(key: RequestKey): RecordedResponse? {
            val list = map[key] ?: return null
            val index = cursor[key] ?: 0
            if (index !in list.indices) return null
            cursor[key] = index + 1
            return list[index]
        }

        val exact = request.asKey(normalizers)
        val fallback = if (request.bodySha256 != null) {
            RequestKey(exact.method, exact.url, null)
        } else {
            null
        }
        return nextFor(exact) ?: fallback?.let(::nextFor)
    }

    fun resetCursors() {
        cursor.clear()
    }

    fun entries(): Sequence<Triple<RequestKey, Int, RecordedResponse>> = sequence {
        for ((key, list) in map) {
            for (index in list.indices) {
                yield(Triple(key, index, list[index]))
            }
        }
    }
}

internal fun HttpRequestSnapshot.asKey(normalizers: List<UrlNormalizer>): RequestKey {
    var normalizedUrl = url
    for (normalizer in normalizers) {
        val candidate = normalizer.normalize(normalizedUrl)
        if (candidate != normalizedUrl) {
            normalizedUrl = candidate
            break
        }
    }
    return RequestKey(method = method, url = normalizedUrl, bodySha256 = bodySha256)
}
