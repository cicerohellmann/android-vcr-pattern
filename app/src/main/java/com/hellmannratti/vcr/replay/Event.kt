package com.hellmannratti.vcr.replay

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement

/**
 * Event model used for recording and replay.
 * Uses Kotlinx Serialization with a sealed hierarchy and a "type" discriminator to keep the NDJSON stable.
 *
 * ### Task 03: nondeterminism payload shapes
 *
 * For nondeterministic values (time/random/UUID) that screen logic may want to record and replay,
 * this project uses [ActionEvent] with stable `name` + `details` shapes:
 *
 * - `name = "ND_TIME"`
 *   - `details = {"nowMs": <Long>}`
 *
 * - `name = "ND_UUID"`
 *   - `details = {"value": <String>}`
 *
 * - `name = "ND_RANDOM_INT"`
 *   - `details = {"from": <Int>, "until": <Int>, "value": <Int>}`
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface Event {
    /** Schema version for a single NDJSON line. */
    val schema: Int

    /** Monotonic sequence number (strictly increasing within a file). */
    val seq: Long

    /** Optional wall-clock timestamp (milliseconds since epoch). Not used for ordering. */
    val ts: Long?

    /** Optional metadata (e.g., sessionId, appVersion, device, mode). */
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
    val bodySha256: String? = null
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
