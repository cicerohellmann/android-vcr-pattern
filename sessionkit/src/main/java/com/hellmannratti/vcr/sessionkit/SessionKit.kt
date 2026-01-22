package com.hellmannratti.vcr.sessionkit

import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient
import java.io.File

/**
 * SessionKit is the minimal app-facing seam for recording + replay.
 * The app should not need to reference tape/interceptor internals.
 */
class SessionKit(
    private var config: SessionKitConfig,
    baseDir: File,
    private val clock: Clock = SystemClock,
    private val idGenerator: IdGenerator = UuidGenerator,
    private val randomProvider: RandomProvider = KotlinRandomProvider(kotlin.random.Random.Default),
    private val logger: TapeLogger = TapeLogger.NoOp,
    private val urlPatterns: List<UrlPattern> = defaultUrlPatterns()
) {
    private val diagnosticsDir: File = File(baseDir, "sessions").apply { mkdirs() }

    private var _lastTapeLoadErrorFile: File? = null

    /** Last tape-load diagnostics file written by SessionKit (if any). Safe to share with support. */
    val lastTapeLoadErrorFile: File?
        get() = _lastTapeLoadErrorFile

    private var recorder = SessionRecorder(
        baseDir = baseDir,
        logSessionStart = config.mode != Mode.REPLAY,
        clock = clock,
        appVersion = config.appVersion,
        device = config.device
    )

    private var replayerInterceptor: ReplayerInterceptor? = null
    private var okHttp: OkHttpClient = buildOkHttp()
    private var loadedTapeFile: File? = null

    val mode: Mode get() = config.mode
    val file: File get() = recorder.file()

    fun clock(): Clock = clock
    fun random(): RandomProvider = randomProvider
    fun idGenerator(): IdGenerator = idGenerator

    fun networkClient(): NetworkClient = OkHttpNetworkClient { okHttp }

    fun switchMode(newMode: Mode) {
        config = config.copy(mode = newMode)
        okHttp = buildOkHttp()
    }

    /** Load an external tape file and activate it for REPLAY mode. Returns unique request count. */
    fun loadTape(file: File): Int {
        loadedTapeFile = file
        if (config.mode != Mode.REPLAY) return 0
        okHttp = buildOkHttp()

        // Build the tape once to report stats.
        return try {
            val tape = TapeLoader.loadLatestSession(file, urlPatterns, logger)
            tape.uniqueRequestCount
        } catch (t: Throwable) {
            val diag = persistTapeLoadError(tapeFile = file, throwable = t)
            throw IllegalStateException(
                "Failed to load tape. Details written to ${diag.name}. See Logcat tag=SessionKit.",
                t
            )
        }
    }

    /**
     * Reset replay-side effects to make "rewind" deterministic.
     *
     * In REPLAY mode, the HTTP tape must not be consumed destructively; we keep a cursor per
     * request key. When the UI resets to initial state (seeking backwards), call this to reset
     * those cursors so earlier requests can be served again.
     */
    fun resetReplayCursors() {
        replayerInterceptor?.tape?.resetCursors()
    }

    fun clearBuffer() {
        // Important: stop the current background writer before creating a new recorder.
        // Otherwise, two recorders may write concurrently to the same file and produce
        // overlapping/duplicate seq values.
        val baseDir = file.parentFile?.parentFile ?: error("Recorder baseDir missing")
        recorder.close()
        recorder = SessionRecorder(
            baseDir = baseDir,
            logSessionStart = config.mode != Mode.REPLAY,
            clock = clock,
            appVersion = config.appVersion,
            device = config.device
        )

        // Rebuild OkHttp so interceptors capture the new recorder instance.
        okHttp = buildOkHttp()
    }

    fun recordUiEvent(
        screen: String,
        event: String,
        payload: Map<String, JsonElement> = emptyMap(),
        metadata: Map<String, String> = emptyMap()
    ) {
        if (config.mode != Mode.RECORD) return
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

    fun recordAction(
        name: String,
        details: Map<String, JsonElement> = emptyMap(),
        metadata: Map<String, String> = emptyMap()
    ) {
        if (config.mode != Mode.RECORD) return
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

    private fun buildOkHttp(recorderOverride: SessionRecorder? = null): OkHttpClient {
        val activeRecorder = recorderOverride ?: recorder

        return when (config.mode) {
            Mode.PASSTHROUGH -> {
                replayerInterceptor = null
                OkHttpClient.Builder().build()
            }
            Mode.RECORD -> {
                replayerInterceptor = null
                OkHttpClient.Builder()
                    .addInterceptor(
                        RecordingInterceptor(
                            recorder = activeRecorder,
                            clock = clock,
                            idGenerator = idGenerator
                        ) { config.mode }
                    )
                    .build()
            }
            Mode.REPLAY -> {
                val tapeFile = loadedTapeFile ?: config.tapeFile ?: activeRecorder.file()
                try {
                    val replayer = ReplayerInterceptor(logger) { config.mode }.apply {
                        tape = TapeLoader.loadLatestSession(tapeFile, urlPatterns, logger)
                    }
                    replayerInterceptor = replayer
                    OkHttpClient.Builder()
                        .addInterceptor(replayer)
                        .build()
                } catch (e: NoTapeFoundException) {
                    replayerInterceptor = null
                    logger.w("SessionKit", "No tape found, using passthrough mode: ${e.message}")
                    OkHttpClient.Builder().build()
                } catch (t: Throwable) {
                    // Startup / mode switch failures shouldn't crash the app.
                    // Persist diagnostics and fall back to passthrough.
                    replayerInterceptor = null
                    persistTapeLoadError(tapeFile = tapeFile, throwable = t)
                    OkHttpClient.Builder().build()
                }
            }
        }
    }

    private fun persistTapeLoadError(tapeFile: File, throwable: Throwable): File {
        val stack = throwable.stackTraceToString()
        val message = buildString {
            appendLine("Tape load failed")
            appendLine("whenMs=${clock.nowMs()}")
            appendLine("tapeFile=${tapeFile.absolutePath}")
            appendLine("throwable=${throwable::class.qualifiedName}")
            appendLine("message=${throwable.message}")
            appendLine()
            appendLine("--- stacktrace ---")
            appendLine(stack)
        }

        val file = File(diagnosticsDir, "last_tape_load_error.txt")
        runCatching { file.writeText(message) }
        _lastTapeLoadErrorFile = file

        // Logcat capture path via host TapeLogger implementation.
        logger.e("SessionKit", "Tape load failed for ${tapeFile.absolutePath}\n$stack")

        return file
    }

    companion object {
        fun defaultUrlPatterns(): List<UrlPattern> = listOf(
            // Pokemon API
            UrlPattern.fromRetrofitStyle("https://pokeapi.co/api/v2/pokemon/{id}"),
            // GitHub API
            UrlPattern.fromRetrofitStyle("https://api.github.com/users/{name}"),
            UrlPattern.fromRetrofitStyle("https://api.github.com/repos/{name}/{name}"),
            // JSONPlaceholder
            UrlPattern.fromRetrofitStyle("https://jsonplaceholder.typicode.com/posts/{id}"),
            UrlPattern.fromRetrofitStyle("https://jsonplaceholder.typicode.com/users/{id}")
        )
    }
}
