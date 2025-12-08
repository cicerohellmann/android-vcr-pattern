package com.hellmannratti.vcr.replay

import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.ByteString
import java.io.EOFException
import java.util.*

/**
 * OkHttp interceptor that records requests and responses.
 */
class RecordingInterceptor(
    private val recorder: SessionRecorder,
    private val modeProvider: () -> Mode = { Mode.PASSTHROUGH }
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        // Only active in RECORD mode; otherwise pass-through
        if (modeProvider() != Mode.RECORD) return chain.proceed(chain.request())
        val request = chain.request()
        val startNs = System.nanoTime()
        val requestId = UUID.randomUUID().toString()

        val bodyHash = safeBodySha256(request.body)

        // Log request event first
        recorder.log(
            RequestEvent(
                ts = System.currentTimeMillis(),
                requestId = requestId,
                method = request.method,
                url = request.url.toString(),
                bodySha256 = bodyHash
            )
        )

        val response = try {
            chain.proceed(request)
        } catch (t: Throwable) {
            throw t
        }

        val tookMs = (System.nanoTime() - startNs) / 1_000_000

        // Read the body as string, then rebuild the response to keep downstream consumers working
        val mediaType = response.body?.contentType()
        val bodyString = response.body?.string() ?: ""

        val sanitizedHeaders = response.headers.toMultimap().mapKeys { it.key }
            .mapValues { (_, v) -> v.joinToString(",") }
            .filterKeys { k ->
                val lower = k.lowercase()
                lower != "content-encoding" && lower != "content-length"
            }

        recorder.log(
            ResponseEvent(
                ts = System.currentTimeMillis(),
                requestId = requestId,
                code = response.code,
                headers = sanitizedHeaders,
                body = bodyString,
                durationMs = tookMs
            )
        )

        // Rebuild and return the response with consumed body
        val rebuilt = response.newBuilder()
            .protocol(response.protocol.takeIf { it != Protocol.HTTP_2 } ?: Protocol.HTTP_1_1)
            .body(bodyString.toByteArray().toResponseBody(mediaType))
            .build()

        return rebuilt
    }

    private fun safeBodySha256(body: okhttp3.RequestBody?): String? {
        if (body == null) return null
        return try {
            // Skip duplex/one-shot bodies where writeTo cannot be replayed safely
            if (body.isDuplex() || body.isOneShot()) return null

            val buffer = Buffer()
            body.writeTo(buffer)

            // Peek a reasonable amount to avoid OOM for extremely large payloads
            val byteString: ByteString = buffer.snapshot()
            byteString.sha256().hex()
        } catch (_: EOFException) {
            null
        } catch (_: Throwable) {
            null
        }
    }
}
