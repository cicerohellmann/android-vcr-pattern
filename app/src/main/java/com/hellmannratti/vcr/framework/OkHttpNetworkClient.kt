package com.hellmannratti.vcr.framework

import com.hellmannratti.vcr.replay.NetworkClient
import com.hellmannratti.vcr.replay.NetworkResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OkHttpNetworkClient(
    private val clientProvider: () -> OkHttpClient
) : NetworkClient {

    override suspend fun get(url: String, headers: Map<String, String>): NetworkResponse {
        val request = Request.Builder()
            .url(url)
            .apply {
                headers.forEach { (k, v) -> addHeader(k, v) }
            }
            .build()

        return execute(request)
    }

    override suspend fun post(
        url: String,
        body: String,
        contentType: String,
        headers: Map<String, String>
    ): NetworkResponse {
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(contentType.toMediaType()))
            .apply {
                headers.forEach { (k, v) -> addHeader(k, v) }
            }
            .build()

        return execute(request)
    }

    private suspend fun execute(request: Request): NetworkResponse = withContext(Dispatchers.IO) {
        val response = clientProvider().newCall(request).execute()
        val code = response.code
        val headers = response.headers.toMultimap()
            .mapValues { (_, v) -> v.joinToString(",") }
        val body = response.body?.string() ?: ""

        NetworkResponse(code = code, headers = headers, body = body)
    }
}
