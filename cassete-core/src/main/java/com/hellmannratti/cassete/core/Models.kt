package com.hellmannratti.cassete.core

data class HttpRequestSnapshot(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val bodySha256: String? = null,
    val bodyUtf8: String? = null
)

data class HttpResponseSnapshot(
    val code: Int,
    val headers: Map<String, String>,
    val bodyUtf8: String,
    val durationMs: Long
)

class NoTapeFoundException(message: String) : RuntimeException(message)
