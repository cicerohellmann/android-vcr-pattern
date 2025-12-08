package com.hellmannratti.vcr.replay

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement

/**
 * Event model used for recording and replay.
 * Uses Kotlinx Serialization with a sealed hierarchy and a "type" discriminator to keep the NDJSON stable.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface Event {
    val ts: Long
}

@Serializable
@SerialName("SESSION_START")
data class SessionStartEvent(
    override val ts: Long,
    val appVersion: String,
    val device: String
) : Event

@Serializable
@SerialName("ACTION")
data class ActionEvent(
    override val ts: Long,
    val name: String,
    val details: Map<String, JsonElement> = emptyMap()
) : Event

@Serializable
@SerialName("REQUEST")
data class RequestEvent(
    override val ts: Long,
    val requestId: String,
    val method: String,
    val url: String,
    val bodySha256: String? = null
) : Event

@Serializable
@SerialName("RESPONSE")
data class ResponseEvent(
    override val ts: Long,
    val requestId: String,
    val code: Int,
    val headers: Map<String, String>,
    val body: String,
    val durationMs: Long
) : Event
