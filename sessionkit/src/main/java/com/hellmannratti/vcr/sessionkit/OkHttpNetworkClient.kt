package com.hellmannratti.vcr.sessionkit

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
        val req = Request.Builder()
            .url(url)
            .get()
            .apply {
                for ((k, v) in headers) addHeader(k, v)
            }
            .build()
        val res = clientProvider().newCall(req).execute()
        val body = res.body?.string() ?: ""
        val headersMap = res.headers.toMultimap().mapValues { (_, v) -> v.joinToString(",") }
        NetworkResponse(res.code, headersMap, body)
    }

    override suspend fun post(
        url: String,
        body: String,
        contentType: String,
        headers: Map<String, String>
    ): NetworkResponse = withContext(Dispatchers.IO) {
        val reqBody = body.toRequestBody(contentType.toMediaType())
        val req = Request.Builder()
            .url(url)
            .post(reqBody)
            .apply {
                for ((k, v) in headers) addHeader(k, v)
            }
            .build()
        val res = clientProvider().newCall(req).execute()
        val resBody = res.body?.string() ?: ""
        val headersMap = res.headers.toMultimap().mapValues { (_, v) -> v.joinToString(",") }
        NetworkResponse(res.code, headersMap, resBody)
    }
}
