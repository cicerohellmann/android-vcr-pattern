package com.hellmannratti.cassete.core

import java.io.File

enum class Mode { RECORD, REPLAY, PASSTHROUGH }

enum class ReplayMissPolicy { THROW, PASSTHROUGH }

enum class MissingTapePolicy { FAIL, PASSTHROUGH }

data class CasseteConfig(
    val initialMode: Mode,
    val tapeFile: File? = null,
    val appVersion: String = "unknown",
    val device: String = "unknown",
    val urlNormalizers: List<UrlNormalizer> = emptyList(),
    val ignoredResponseHeaders: Set<String> = setOf("content-encoding", "content-length"),
    val requestHeaderRedactor: HeaderRedactor = HeaderRedactor.keepAll(),
    val responseHeaderRedactor: HeaderRedactor = HeaderRedactor.keepAll(),
    val requestBodyRedactor: BodyRedactor = BodyRedactor.keepBody(),
    val responseBodyRedactor: BodyRedactor = BodyRedactor.keepBody(),
    val replayMissPolicy: ReplayMissPolicy = ReplayMissPolicy.THROW,
    val missingTapePolicy: MissingTapePolicy = MissingTapePolicy.PASSTHROUGH,
    val simulateLatency: Boolean = false
)

data class TapeLoadResult(
    val file: File,
    val uniqueRequestCount: Int,
    val totalResponses: Int
)

interface CasseteController {
    val mode: kotlinx.coroutines.flow.StateFlow<Mode>
    val recordingFile: File
    val lastTapeLoadErrorFile: File?

    fun switchMode(mode: Mode)
    fun loadTape(file: File): TapeLoadResult
    fun resetReplayCursors()
    fun clearBuffer()
    fun recordUiEvent(
        screen: String,
        event: String,
        payload: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
        metadata: Map<String, String> = emptyMap()
    )
    fun recordAction(
        name: String,
        details: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
        metadata: Map<String, String> = emptyMap()
    )
}
