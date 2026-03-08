package com.hellmannratti.cassete.ktor

import com.hellmannratti.cassete.core.CasseteRuntime
import com.hellmannratti.cassete.core.HttpRequestSnapshot
import com.hellmannratti.cassete.core.HttpResponseSnapshot
import com.hellmannratti.cassete.core.Mode
import com.hellmannratti.cassete.core.NoTapeFoundException
import com.hellmannratti.cassete.core.ReplayMissPolicy
import io.ktor.client.call.HttpClientCall
import io.ktor.client.call.save
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.HttpResponseData
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.delay
import java.security.MessageDigest

class CasseteKtorConfig {
    var controller: CasseteRuntime? = null
}

val CasseteKtor = createClientPlugin("CasseteKtor", ::CasseteKtorConfig) {
    val controller = requireNotNull(pluginConfig.controller) { "CasseteKtor requires a controller" }
    val ktorClient = client

    on(Send) { request ->
        when (controller.mode.value) {
            Mode.PASSTHROUGH -> proceed(request)
            Mode.REPLAY -> {
                val snapshot = request.toSnapshot()
                val replayed = controller.replayResponse(snapshot)
                if (replayed != null) {
                    if (controller.shouldSimulateLatency() && replayed.durationMs > 0) {
                        delay(replayed.durationMs)
                    }
                    request.replayedCall(ktorClient, replayed)
                } else if (controller.replayMissPolicy() == ReplayMissPolicy.THROW) {
                    throw NoTapeFoundException("No recorded response for ${request.method.value} ${request.url.buildString()}")
                } else {
                    proceed(request)
                }
            }
            Mode.RECORD -> recordRequest(controller, request)
        }
    }
}

private suspend fun io.ktor.client.plugins.api.Send.Sender.recordRequest(
    controller: CasseteRuntime,
    request: HttpRequestBuilder
): HttpClientCall {
    val startNs = System.nanoTime()
    val snapshot = request.toSnapshot()
    val requestId = controller.recordHttpRequest(snapshot)
    val call = proceed(request)
    val savedCall = call.save()
    val durationMs = (System.nanoTime() - startNs) / 1_000_000

    if (requestId != null) {
        controller.recordHttpResponse(requestId, savedCall.toSnapshot(durationMs))
    }

    return savedCall
}

private fun HttpRequestBuilder.toSnapshot(): HttpRequestSnapshot {
    val bodyUtf8 = bodyUtf8()
    return HttpRequestSnapshot(
        method = method.value,
        url = url.buildString(),
        headers = headers.entries().associate { it.key to it.value.joinToString(",") },
        bodySha256 = bodyUtf8?.sha256(),
        bodyUtf8 = bodyUtf8
    )
}

private fun HttpRequestBuilder.bodyUtf8(): String? {
    return when (val currentBody = body) {
        is String -> currentBody
        is TextContent -> currentBody.text
        is OutgoingContent.ByteArrayContent -> currentBody.bytes().toString(Charsets.UTF_8)
        is OutgoingContent.ReadChannelContent -> null
        is OutgoingContent.WriteChannelContent -> null
        is OutgoingContent.NoContent -> null
        else -> null
    }
}

private suspend fun HttpClientCall.toSnapshot(durationMs: Long): HttpResponseSnapshot {
    return HttpResponseSnapshot(
        code = response.status.value,
        headers = response.headers.entries().associate { it.key to it.value.joinToString(",") },
        bodyUtf8 = response.bodyAsText(),
        durationMs = durationMs
    )
}

@OptIn(InternalAPI::class)
private fun HttpRequestBuilder.replayedCall(
    client: io.ktor.client.HttpClient,
    response: HttpResponseSnapshot
): HttpClientCall {
    val requestData = build()
    val responseData = HttpResponseData(
        statusCode = io.ktor.http.HttpStatusCode.fromValue(response.code),
        requestTime = GMTDate(),
        headers = Headers.build {
            for ((name, value) in response.headers) {
                append(name, value)
            }
        },
        version = HttpProtocolVersion.HTTP_1_1,
        body = ByteReadChannel(response.bodyUtf8),
        callContext = requestData.executionContext
    )
    return HttpClientCall(client, requestData, responseData)
}

private fun String.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(toByteArray()).joinToString(separator = "") { "%02x".format(it) }
}
