package com.hellmannratti.cassete.okhttp

import com.hellmannratti.cassete.core.CasseteRuntime
import com.hellmannratti.cassete.core.HttpRequestSnapshot
import com.hellmannratti.cassete.core.HttpResponseSnapshot
import com.hellmannratti.cassete.core.Mode
import com.hellmannratti.cassete.core.NoTapeFoundException
import com.hellmannratti.cassete.core.ReplayMissPolicy
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.ByteString
import java.io.EOFException

class CasseteOkHttpInterceptor(
    private val controller: CasseteRuntime
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        return when (controller.mode.value) {
            Mode.PASSTHROUGH -> chain.proceed(request)
            Mode.REPLAY -> interceptReplay(chain, request)
            Mode.RECORD -> interceptRecord(chain, request)
        }
    }

    private fun interceptReplay(chain: Interceptor.Chain, request: Request): Response {
        val snapshot = request.toSnapshot()
        val replayed = controller.replayResponse(snapshot)
        if (replayed != null) {
            if (controller.shouldSimulateLatency() && replayed.durationMs > 0) {
                Thread.sleep(replayed.durationMs)
            }
            return replayed.toOkHttpResponse(request)
        }

        if (controller.replayMissPolicy() == ReplayMissPolicy.THROW) {
            throw NoTapeFoundException("No recorded response for ${request.method} ${request.url}")
        }

        return chain.proceed(request)
    }

    private fun interceptRecord(chain: Interceptor.Chain, request: Request): Response {
        val startNs = System.nanoTime()
        val requestSnapshot = request.toSnapshot()
        val requestId = controller.recordHttpRequest(requestSnapshot)
        val response = chain.proceed(request)
        val tookMs = (System.nanoTime() - startNs) / 1_000_000

        val responseSnapshot = response.toSnapshot(durationMs = tookMs)
        if (requestId != null) {
            controller.recordHttpResponse(requestId, responseSnapshot)
        }

        return responseSnapshot.toOkHttpResponse(request, protocol = response.protocol)
    }

    private fun Request.toSnapshot(): HttpRequestSnapshot {
        val headersMap = headers.toMultimap().mapValues { (_, values) -> values.joinToString(",") }
        return HttpRequestSnapshot(
            method = method,
            url = url.toString(),
            headers = headersMap,
            bodySha256 = safeBodySha256(body),
            bodyUtf8 = safeBodyUtf8(body)
        )
    }

    private fun Response.toSnapshot(durationMs: Long): HttpResponseSnapshot {
        val mediaType = body?.contentType()
        val bodyString = body?.string() ?: ""
        val headersMap = headers.toMultimap().mapValues { (_, values) -> values.joinToString(",") }
        return HttpResponseSnapshot(
            code = code,
            headers = headersMap,
            bodyUtf8 = bodyString,
            durationMs = durationMs
        ).copy(
            headers = headersMap,
            bodyUtf8 = bodyString.let {
                if (mediaType != null) {
                    it
                } else {
                    it
                }
            }
        )
    }

    private fun HttpResponseSnapshot.toOkHttpResponse(
        request: Request,
        protocol: Protocol = Protocol.HTTP_1_1
    ): Response {
        val contentType = headers["Content-Type"]?.toMediaTypeOrNull()
            ?: headers["content-type"]?.toMediaTypeOrNull()
        return Response.Builder()
            .request(request)
            .protocol(protocol.takeIf { it != Protocol.HTTP_2 } ?: Protocol.HTTP_1_1)
            .code(code)
            .message("OK")
            .apply {
                for ((name, value) in headers) {
                    addHeader(name, value)
                }
            }
            .body(bodyUtf8.toByteArray().toResponseBody(contentType))
            .build()
    }

    private fun safeBodySha256(body: okhttp3.RequestBody?): String? {
        if (body == null) return null
        return try {
            if (body.isDuplex() || body.isOneShot()) return null

            val buffer = Buffer()
            body.writeTo(buffer)
            buffer.snapshot().sha256().hex()
        } catch (_: EOFException) {
            null
        } catch (_: Throwable) {
            null
        }
    }

    private fun safeBodyUtf8(body: okhttp3.RequestBody?): String? {
        if (body == null) return null
        return try {
            if (body.isDuplex() || body.isOneShot()) return null

            val buffer = Buffer()
            body.writeTo(buffer)
            val byteString: ByteString = buffer.snapshot()
            byteString.utf8()
        } catch (_: Throwable) {
            null
        }
    }
}
