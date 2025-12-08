package com.hellmannratti.zombie.replay

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.ByteString
import java.io.EOFException

/**
 * OkHttp interceptor that serves responses from a loaded ReplayTape.
 */
class ReplayerInterceptor(
    private val logger: TapeLogger = TapeLogger.NoOp,
    private val modeProvider: () -> Mode = { Mode.PASSTHROUGH }
) : Interceptor {
    @Volatile
    var tape: ReplayTape? = null

    override fun intercept(chain: Interceptor.Chain): Response {
            // Only active in REPLAY mode; otherwise pass-through
            if (modeProvider() != Mode.REPLAY) return chain.proceed(chain.request())
        val request = chain.request()
        val bodySha = safeBodySha256(request.body)

        logger.d("ReplayerInterceptor", "Processing request: ${request.method} ${request.url} (bodySha256=${bodySha ?: "null"})")

        var match = tape?.find(request, bodySha)
        if (match == null) {
            logger.e("ReplayerInterceptor", "No match found in tape for: ${request.method} ${request.url}")
            throw NoTapeFoundException("No recorded response found for: ${request.method} ${request.url}")
        }

        logger.i("ReplayerInterceptor", "Match found! Serving recorded response: code=${match.code}, durationMs=${match.durationMs}")

        // Simulate original latency (interceptors run off main thread)
        if (match.durationMs > 0) {
            try { Thread.sleep(match.durationMs) } catch (_: InterruptedException) { }
        }

        val media = (match.headers["content-type"] ?: "application/json; charset=utf-8").toMediaType()
        val builder = Response.Builder()
            .code(match.code)
            .message("")
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .body(match.body.toResponseBody(media))

        match.headers
            .filterKeys { it.lowercase() !in setOf("content-encoding", "content-length") }
            .forEach { (k, v) -> builder.addHeader(k, v) }

        return builder.build()
    }

    private fun safeBodySha256(body: okhttp3.RequestBody?): String? {
        if (body == null) return null
        return try {
            if (body.isDuplex() || body.isOneShot()) return null
            val buffer = Buffer()
            body.writeTo(buffer)
            val byteString: ByteString = buffer.snapshot()
            byteString.sha256().hex()
        } catch (_: EOFException) {
            null
        } catch (_: Throwable) {
            null
        }
    }
}
