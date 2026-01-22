package com.hellmannratti.vcr.sessionkit

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.ByteString

internal class ReplayerInterceptor(
    private val logger: TapeLogger = TapeLogger.NoOp,
    private val modeProvider: () -> Mode = { Mode.PASSTHROUGH }
) : Interceptor {
    var tape: ReplayTape? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        if (modeProvider() != Mode.REPLAY) return chain.proceed(chain.request())
        val request = chain.request()

        val bodyHash = safeBodySha256(request.body)
        val recorded = tape?.find(request, bodyHash)
        if (recorded == null) {
            logger.e("Replayer", "No recorded response for ${request.method} ${request.url}")
            throw NoTapeFoundException("No recorded response for ${request.method} ${request.url}")
        }

        val contentType = recorded.headers["Content-Type"]?.toMediaTypeOrNull()

        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(recorded.code)
            .message("OK")
            .apply {
                for ((k, v) in recorded.headers) {
                    addHeader(k, v)
                }
            }
            .body(recorded.body.toByteArray().toResponseBody(contentType))
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
        } catch (_: Throwable) {
            null
        }
    }
}
