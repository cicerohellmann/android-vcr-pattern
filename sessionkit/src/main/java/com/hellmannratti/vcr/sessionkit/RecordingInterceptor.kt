package com.hellmannratti.vcr.sessionkit

import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.ByteString
import java.io.EOFException

internal class RecordingInterceptor(
    private val recorder: SessionRecorder,
    private val clock: Clock = SystemClock,
    private val idGenerator: IdGenerator = UuidGenerator,
    private val modeProvider: () -> Mode = { Mode.PASSTHROUGH }
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        if (modeProvider() != Mode.RECORD) return chain.proceed(chain.request())
        val request = chain.request()
        val startNs = clock.nowNs()
        val requestId = idGenerator.uuid()

        val bodyHash = safeBodySha256(request.body)

        recorder.log(
            RequestEvent(
                seq = -1,
                ts = clock.nowMs(),
                requestId = requestId,
                method = request.method,
                url = request.url.toString(),
                bodySha256 = bodyHash
            )
        )

        val response = chain.proceed(request)

        val tookMs = (clock.nowNs() - startNs) / 1_000_000

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
                seq = -1,
                ts = clock.nowMs(),
                requestId = requestId,
                code = response.code,
                headers = sanitizedHeaders,
                body = bodyString,
                durationMs = tookMs
            )
        )

        return response.newBuilder()
            .protocol(response.protocol.takeIf { it != Protocol.HTTP_2 } ?: Protocol.HTTP_1_1)
            .body(bodyString.toByteArray().toResponseBody(mediaType))
            .build()
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
