package com.hellmannratti.vcr.replay

/**
 * Minimal network abstraction used by screen logic.
 *
 * In RECORD/PASSTHROUGH this typically delegates to OkHttp.
 * In REPLAY it can delegate to an OkHttp client that has [ReplayerInterceptor] installed,
 * or be implemented as a pure tape reader.
 */
interface NetworkClient {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): NetworkResponse
    suspend fun post(
        url: String,
        body: String,
        contentType: String = "application/json",
        headers: Map<String, String> = emptyMap()
    ): NetworkResponse
}

data class NetworkResponse(
    val code: Int,
    val headers: Map<String, String>,
    val body: String
) {
    fun requireSuccess(): NetworkResponse {
        if (code !in 200..299) error("HTTP $code")
        return this
    }
}
