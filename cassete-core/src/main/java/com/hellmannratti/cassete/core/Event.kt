package com.hellmannratti.cassete.core

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface Event {
    val schema: Int
    val seq: Long
    val ts: Long?
    val metadata: Map<String, String>
}

@Serializable
@SerialName("SESSION_START")
data class SessionStartEvent(
    override val schema: Int = 1,
    override val seq: Long,
    override val ts: Long? = null,
    override val metadata: Map<String, String> = emptyMap(),
    val appVersion: String,
    val device: String
) : Event

@Serializable
@SerialName("ACTION")
data class ActionEvent(
    override val schema: Int = 1,
    override val seq: Long,
    override val ts: Long? = null,
    override val metadata: Map<String, String> = emptyMap(),
    val name: String,
    val details: Map<String, JsonElement> = emptyMap()
) : Event

@Serializable
@SerialName("UI_EVENT")
data class UiEventRecorded(
    override val schema: Int = 1,
    override val seq: Long,
    override val ts: Long? = null,
    override val metadata: Map<String, String> = emptyMap(),
    val screen: String,
    val event: String,
    val payload: Map<String, JsonElement> = emptyMap()
) : Event

@Serializable
@SerialName("REQUEST")
data class RequestEvent(
    override val schema: Int = 1,
    override val seq: Long,
    override val ts: Long? = null,
    override val metadata: Map<String, String> = emptyMap(),
    val requestId: String,
    val method: String,
    val url: String,
    val bodySha256: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val bodyUtf8: String? = null
) : Event

@Serializable
@SerialName("RESPONSE")
data class ResponseEvent(
    override val schema: Int = 1,
    override val seq: Long,
    override val ts: Long? = null,
    override val metadata: Map<String, String> = emptyMap(),
    val requestId: String,
    val code: Int,
    val headers: Map<String, String>,
    val body: String,
    val durationMs: Long
) : Event
