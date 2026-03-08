package com.hellmannratti.cassete.okhttp

import com.hellmannratti.cassete.core.NetworkClient
import com.hellmannratti.cassete.core.NetworkResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OkHttpNetworkClient(
    private val clientProvider: () -> OkHttpClient
) : NetworkClient {
    override suspend fun get(url: String, headers: Map<String, String>): NetworkResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .get()
            .apply {
                for ((name, value) in headers) {
                    addHeader(name, value)
                }
            }
            .build()
        execute(request)
    }

    override suspend fun post(
        url: String,
        body: String,
        contentType: String,
        headers: Map<String, String>
    ): NetworkResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(contentType.toMediaType()))
            .apply {
                for ((name, value) in headers) {
                    addHeader(name, value)
                }
            }
            .build()
        execute(request)
    }

    private fun execute(request: Request): NetworkResponse {
        clientProvider().newCall(request).execute().use { response ->
            return NetworkResponse(
                code = response.code,
                headers = response.headers.toMultimap().mapValues { (_, values) -> values.joinToString(",") },
                body = response.body?.string() ?: ""
            )
        }
    }
}
