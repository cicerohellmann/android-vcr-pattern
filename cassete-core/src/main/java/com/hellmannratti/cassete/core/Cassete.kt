package com.hellmannratti.cassete.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonElement
import java.io.File

object Cassete {
    fun create(
        config: CasseteConfig,
        baseDir: File,
        clock: Clock = SystemClock,
        idGenerator: IdGenerator = UuidGenerator,
        logger: TapeLogger = TapeLogger.NoOp
    ): CasseteRuntime {
        return CasseteRuntime(
            config = config,
            baseDir = baseDir,
            clock = clock,
            idGenerator = idGenerator,
            logger = logger
        )
    }
}

class CasseteRuntime(
    private var config: CasseteConfig,
    private val baseDir: File,
    private val clock: Clock,
    private val idGenerator: IdGenerator,
    private val logger: TapeLogger
) : CasseteController {
    private val diagnosticsDir = File(baseDir, "sessions").apply { mkdirs() }
    private val modeFlow = MutableStateFlow(config.initialMode)

    private var recorder = SessionRecorder(
        baseDir = baseDir,
        logSessionStart = config.initialMode != Mode.REPLAY,
        clock = clock,
        appVersion = config.appVersion,
        device = config.device
    )
    private var loadedTapeFile: File? = config.tapeFile
    private var activeTape: ReplayTape? = null
    private var tapeLoadErrorFile: File? = null

    override val mode: StateFlow<Mode> = modeFlow

    override val recordingFile: File
        get() = recorder.file()

    override val lastTapeLoadErrorFile: File?
        get() = tapeLoadErrorFile

    fun nextRequestId(): String = idGenerator.uuid()

    fun recordHttpRequest(snapshot: HttpRequestSnapshot): String? {
        if (mode.value != Mode.RECORD) return null

        val requestId = nextRequestId()
        recorder.log(
            RequestEvent(
                seq = -1,
                ts = clock.nowMs(),
                requestId = requestId,
                method = snapshot.method,
                url = normalizeUrl(snapshot.url),
                bodySha256 = snapshot.bodySha256,
                headers = sanitizeHeaders(snapshot.headers, config.requestHeaderRedactor),
                bodyUtf8 = snapshot.bodyUtf8?.let { config.requestBodyRedactor.redact(null, it) }
            )
        )
        return requestId
    }

    fun recordHttpResponse(requestId: String, snapshot: HttpResponseSnapshot) {
        if (mode.value != Mode.RECORD) return

        recorder.log(
            ResponseEvent(
                seq = -1,
                ts = clock.nowMs(),
                requestId = requestId,
                code = snapshot.code,
                headers = sanitizeResponseHeaders(snapshot.headers),
                body = config.responseBodyRedactor.redact(
                    snapshot.headers["Content-Type"] ?: snapshot.headers["content-type"],
                    snapshot.bodyUtf8
                ),
                durationMs = snapshot.durationMs
            )
        )
    }

    fun replayResponse(snapshot: HttpRequestSnapshot): HttpResponseSnapshot? {
        if (mode.value != Mode.REPLAY) return null

        val tape = ensureReplayTapeLoaded() ?: return null
        val recorded = tape.find(
            snapshot.copy(
                url = normalizeUrl(snapshot.url)
            )
        ) ?: return null

        return recorded.toSnapshot()
    }

    fun replayMissPolicy(): ReplayMissPolicy = config.replayMissPolicy

    fun shouldSimulateLatency(): Boolean = config.simulateLatency

    override fun switchMode(mode: Mode) {
        if (mode == Mode.REPLAY) {
            recorder.flush()
        }
        modeFlow.value = mode
        if (mode != Mode.REPLAY) {
            activeTape = null
        }
    }

    override fun loadTape(file: File): TapeLoadResult {
        recorder.flush()
        loadedTapeFile = file
        activeTape = null
        val tape = try {
            TapeLoader.loadLatestSession(file, config.urlNormalizers, logger)
        } catch (t: Throwable) {
            val diag = persistTapeLoadError(file, t)
            throw IllegalStateException(
                "Failed to load tape. Details written to ${diag.name}. See Logcat tag=Cassete.",
                t
            )
        }

        activeTape = tape
        tapeLoadErrorFile = null
        return TapeLoadResult(
            file = file,
            uniqueRequestCount = tape.uniqueRequestCount,
            totalResponses = tape.entries().count()
        )
    }

    override fun resetReplayCursors() {
        activeTape?.resetCursors()
    }

    override fun clearBuffer() {
        recorder.close()
        recorder = SessionRecorder(
            baseDir = baseDir,
            logSessionStart = mode.value != Mode.REPLAY,
            clock = clock,
            appVersion = config.appVersion,
            device = config.device
        )
        activeTape = null
    }

    override fun recordUiEvent(
        screen: String,
        event: String,
        payload: Map<String, JsonElement>,
        metadata: Map<String, String>
    ) {
        if (mode.value != Mode.RECORD) return
        recorder.log(
            UiEventRecorded(
                seq = -1,
                ts = clock.nowMs(),
                metadata = metadata,
                screen = screen,
                event = event,
                payload = payload
            )
        )
    }

    override fun recordAction(
        name: String,
        details: Map<String, JsonElement>,
        metadata: Map<String, String>
    ) {
        if (mode.value != Mode.RECORD) return
        recorder.log(
            ActionEvent(
                seq = -1,
                ts = clock.nowMs(),
                metadata = metadata,
                name = name,
                details = details
            )
        )
    }

    private fun ensureReplayTapeLoaded(): ReplayTape? {
        activeTape?.let { return it }
        recorder.flush()
        val tapeFile = loadedTapeFile ?: config.tapeFile ?: recorder.file()
        return try {
            TapeLoader.loadLatestSession(tapeFile, config.urlNormalizers, logger).also {
                activeTape = it
                tapeLoadErrorFile = null
            }
        } catch (e: NoTapeFoundException) {
            logger.w("Cassete", "No tape found, replay will use ${config.missingTapePolicy.name.lowercase()} mode: ${e.message}")
            if (config.missingTapePolicy == MissingTapePolicy.FAIL) throw e else null
        } catch (t: Throwable) {
            persistTapeLoadError(tapeFile, t)
            if (config.missingTapePolicy == MissingTapePolicy.FAIL) {
                throw IllegalStateException("Failed to load replay tape from ${tapeFile.absolutePath}", t)
            }
            null
        }
    }

    private fun persistTapeLoadError(tapeFile: File, throwable: Throwable): File {
        val file = File(diagnosticsDir, "last_tape_load_error.txt")
        val message = buildString {
            appendLine("Tape load failed")
            appendLine("whenMs=${clock.nowMs()}")
            appendLine("tapeFile=${tapeFile.absolutePath}")
            appendLine("throwable=${throwable::class.qualifiedName}")
            appendLine("message=${throwable.message}")
            appendLine()
            appendLine("--- stacktrace ---")
            appendLine(throwable.stackTraceToString())
        }
        runCatching { file.writeText(message) }
        tapeLoadErrorFile = file
        logger.e("Cassete", "Tape load failed for ${tapeFile.absolutePath}\n${throwable.stackTraceToString()}")
        return file
    }

    private fun normalizeUrl(url: String): String {
        var current = url
        for (normalizer in config.urlNormalizers) {
            val candidate = normalizer.normalize(current)
            if (candidate != current) {
                current = candidate
                break
            }
        }
        return current
    }

    private fun sanitizeHeaders(
        headers: Map<String, String>,
        redactor: HeaderRedactor
    ): Map<String, String> = buildMap {
        for ((name, value) in headers) {
            redactor.redact(name, value)?.let { put(name, it) }
        }
    }

    private fun sanitizeResponseHeaders(headers: Map<String, String>): Map<String, String> {
        val ignored = config.ignoredResponseHeaders.map { it.lowercase() }.toSet()
        val filtered = headers.filterKeys { it.lowercase() !in ignored }
        return sanitizeHeaders(filtered, config.responseHeaderRedactor)
    }
}
