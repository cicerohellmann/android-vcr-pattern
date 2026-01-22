package com.hellmannratti.vcr

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hellmannratti.vcr.sessionkit.Clock
import com.hellmannratti.vcr.sessionkit.Mode
import com.hellmannratti.vcr.sessionkit.NetworkClient
import com.hellmannratti.vcr.sessionkit.RandomProvider
import com.hellmannratti.vcr.sessionkit.SessionPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

class ApiTestViewModel(
    private val app: VcrApp,
    private val clock: Clock,
    private val random: RandomProvider,
    private val network: NetworkClient
) : ViewModel() {

    private var fetchJob: Job? = null

    private val replayHandler = object : ReplayController.Handler {
        override suspend fun emitUiEvent(event: SessionPlayer.RecordedUiEvent) {
            decodeRecordedUiEvent(event)?.let { decoded ->
                onEvent(decoded, fromPlayer = true)
            }
        }

        override suspend fun resetToInitialState() {
            resetForReplay()
        }
    }

    private val player: SessionPlayer = app.replayController.player

    private val _state = MutableStateFlow(ApiUiState(mode = app.currentMode.value))
    val state: StateFlow<ApiUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<UiEffect>()
    val effects: SharedFlow<UiEffect> = _effects.asSharedFlow()

    init {
        app.replayController.attachHandler(replayHandler)
        viewModelScope.launch {
            app.currentMode.collectLatest { mode ->
                _state.update { it.copy(mode = mode) }
            }
        }

        viewModelScope.launch {
            player.state.collectLatest { ps ->
                _state.update {
                    it.copy(
                        player = PlayerUiState(
                            loaded = ps.loaded,
                            playing = ps.playing,
                            position = ps.position,
                            total = ps.total
                        )
                    )
                }
            }
        }
    }

    override fun onCleared() {
        app.replayController.detachHandler(replayHandler)
        super.onCleared()
    }

    fun onEvent(event: UiEvent, fromPlayer: Boolean = false) {
        if (!fromPlayer && isUserInputGated(event)) {
            viewModelScope.launch {
                _effects.emit(UiEffect.ShowToast("Player active: live inputs are gated"))
            }
            return
        }

        if (!fromPlayer) recordUiEventIfNeeded(event)
        when (event) {
            UiEvent.FetchRandomPokemon -> fetchRandomPokemon()
            is UiEvent.FetchFromApi -> fetchFromApi(event.apiType)
            UiEvent.ToggleMode -> toggleMode()
            UiEvent.ShareSessionLog -> shareSessionLog()
            UiEvent.DownloadSessionLog -> downloadSessionLog()
            UiEvent.ShowClearBufferDialog -> _state.update { it.copy(showClearBufferDialog = true) }
            UiEvent.DismissClearBufferDialog -> _state.update { it.copy(showClearBufferDialog = false) }
            UiEvent.ShowDeleteFileDialog -> _state.update { it.copy(showDeleteFileDialog = true) }
            UiEvent.DismissDeleteFileDialog -> _state.update { it.copy(showDeleteFileDialog = false) }
            UiEvent.ConfirmClearBuffer -> clearBuffer()
            UiEvent.ConfirmDeleteFile -> deleteSessionFile()
            UiEvent.RequestTapePick -> launchTapePicker()
            is UiEvent.OnTapePicked -> loadTapeFromUri(event.uri)
            UiEvent.DismissModal -> dismissModal()

            UiEvent.PlayerTogglePlay -> togglePlay()
            UiEvent.PlayerPause -> player.pause()
            UiEvent.PlayerPlay -> player.play(stepDelayMs = 0L)
            UiEvent.PlayerStepFwd -> viewModelScope.launch { player.stepForward() }
            UiEvent.PlayerStepBack -> viewModelScope.launch { player.stepBack() }
            is UiEvent.PlayerSeek -> viewModelScope.launch { player.seek(event.position) }
            UiEvent.PlayerStop -> stopReplay()
        }
    }

    private fun stopReplay() {
        player.stopAndUnload()
        resetForReplay()
    }

    private fun fetchRandomPokemon() {
        _state.update {
            it.copy(
                content = ScreenContent.Loading,
                showPokemonModal = false,
                showGenericModal = false
            )
        }
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            runCatching { fetchRandomPokemonInternal() }
                .onSuccess { pokemon ->
                    _state.update {
                        it.copy(
                            content = ScreenContent.PokemonLoaded(pokemon),
                            showPokemonModal = true
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            content = ScreenContent.Error(error.message ?: "Unknown error")
                        )
                    }
                }
        }
    }

    private fun fetchFromApi(apiType: ApiType) {
        _state.update {
            it.copy(
                content = ScreenContent.Loading,
                showPokemonModal = false,
                showGenericModal = false
            )
        }
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            runCatching { fetchFromApiInternal(apiType) }
                .onSuccess { response ->
                    _state.update {
                        it.copy(
                            content = ScreenContent.GenericLoaded(response, apiType),
                            showGenericModal = true
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            content = ScreenContent.Error(error.message ?: "Unknown error")
                        )
                    }
                }
        }
    }

    private fun toggleMode() {
        val newMode = when (state.value.mode) {
            Mode.RECORD -> Mode.REPLAY
            Mode.REPLAY -> Mode.RECORD
            Mode.PASSTHROUGH -> Mode.RECORD
        }
        viewModelScope.launch {
            runCatching { app.switchMode(newMode) }
                .onSuccess {
                    _effects.emit(UiEffect.ShowToast("Switched to ${newMode.name} mode"))
                }
                .onFailure { error ->
                    _effects.emit(UiEffect.ShowToast("Failed to switch mode: ${error.message}"))
                }
        }
    }

    private fun shareSessionLog() {
        viewModelScope.launch {
            runCatching {
                val filesToShare = buildList {
                    add(app.sessionKit.file)
                    app.sessionKit.lastTapeLoadErrorFile?.takeIf { it.exists() }?.let { add(it) }
                }

                val authority = "${app.packageName}.fileprovider"

                val intent = if (filesToShare.size == 1) {
                    val uri = FileProvider.getUriForFile(app, authority, filesToShare.single())
                    Intent(Intent.ACTION_SEND).apply {
                        type = "application/x-ndjson"
                        putExtra(Intent.EXTRA_STREAM, uri)
                    }
                } else {
                    val uris = ArrayList<Uri>(filesToShare.size)
                    for (f in filesToShare) {
                        uris.add(FileProvider.getUriForFile(app, authority, f))
                    }
                    Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "*/*"
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    }
                }.apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(Intent.EXTRA_SUBJECT, "VCR session artifacts")
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "Attached: events.ndjson" + if (filesToShare.size > 1) ", last_tape_load_error.txt" else ""
                    )
                }

                app.startActivity(Intent.createChooser(intent, "Share session artifacts")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure { error ->
                _effects.emit(UiEffect.ShowToast("Failed to share: ${error.message}"))
            }
        }
    }

    private fun downloadSessionLog() {
        viewModelScope.launch {
            val fileName = "vcr_session_${clock.nowMs()}.ndjson"
            runCatching {
                val sourceFile = app.sessionKit.file

                withContext(Dispatchers.IO) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val contentValues = ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                            put(MediaStore.Downloads.MIME_TYPE, "application/x-ndjson")
                            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        }

                        val uri = app.contentResolver.insert(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            contentValues
                        )
                        uri?.let {
                            app.contentResolver.openOutputStream(it)?.use { outputStream ->
                                sourceFile.inputStream().use { inputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }
                        } ?: error("Failed to create file in Downloads")
                    } else {
                        @Suppress("DEPRECATION")
                        val downloadsDir =
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        downloadsDir.mkdirs()
                        val destFile = File(downloadsDir, fileName)
                        sourceFile.copyTo(destFile, overwrite = true)
                    }
                }
                fileName
            }.onSuccess { savedName ->
                _effects.emit(UiEffect.ShowToast("Session log saved to Downloads/$savedName"))
            }.onFailure { error ->
                _effects.emit(UiEffect.ShowToast("Failed to download: ${error.message}"))
            }
        }
    }

    private fun clearBuffer() {
        _state.update { it.copy(showClearBufferDialog = false) }
        viewModelScope.launch {
            runCatching { app.clearBuffer() }
                .onSuccess { _effects.emit(UiEffect.ShowToast("Buffer cleared")) }
                .onFailure { error ->
                    _effects.emit(UiEffect.ShowToast("Failed to clear buffer: ${error.message}"))
                }
        }
    }

    private fun deleteSessionFile() {
        _state.update { it.copy(showDeleteFileDialog = false) }
        viewModelScope.launch {
            runCatching {
                val sessionFile = File(app.filesDir, "sessions/events.ndjson")
                if (sessionFile.exists()) {
                    sessionFile.delete()
                }
                sessionFile.parentFile?.mkdirs()
                sessionFile.createNewFile()
            }.onSuccess {
                _effects.emit(UiEffect.ShowToast("Session file deleted"))
            }.onFailure { error ->
                _effects.emit(UiEffect.ShowToast("Failed to delete session file: ${error.message}"))
            }
        }
    }

    private fun launchTapePicker() {
        viewModelScope.launch {
            _effects.emit(UiEffect.LaunchTapePicker)
        }
    }

    private fun loadTapeFromUri(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            runCatching {
                val destFile = File(app.sessionKit.file.parentFile, "selected_tape.ndjson")

                withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                app.switchMode(Mode.REPLAY)
                app.loadTapeFile(destFile)
                player.loadLatestSession(destFile)
            }.onSuccess { count ->
                _effects.emit(UiEffect.ShowToast("Tape loaded successfully: $count unique requests"))
            }.onFailure { error ->
                _effects.emit(UiEffect.ShowToast("Failed to load tape: ${error.message}"))
            }
        }
    }

    private fun togglePlay() {
        val ps = state.value.player
        if (!ps.loaded) return
        if (ps.playing) player.pause() else player.play(stepDelayMs = 0L)
    }

    private fun resetForReplay() {
        fetchJob?.cancel()
        app.sessionKit.resetReplayCursors()
        _state.update {
            it.copy(
                content = ScreenContent.Idle,
                showPokemonModal = false,
                showGenericModal = false,
                showClearBufferDialog = false,
                showDeleteFileDialog = false
            )
        }
    }

    private fun decodeRecordedUiEvent(recorded: SessionPlayer.RecordedUiEvent): UiEvent? {
        if (recorded.screen != "ApiTest") return null
        return when (recorded.event) {
            "FetchRandomPokemon" -> UiEvent.FetchRandomPokemon
            "FetchFromApi" -> {
                val apiTypeName = (recorded.payload["apiType"] as? JsonPrimitive)?.content
                val apiType = when (apiTypeName) {
                    "GitHub" -> ApiType.GitHub("torvalds")
                    "JsonPlaceholder" -> ApiType.JsonPlaceholder("posts")
                    "Pokemon" -> ApiType.Pokemon(null)
                    "Custom" -> null
                    else -> null
                } ?: return null
                UiEvent.FetchFromApi(apiType)
            }
            "DismissModal" -> UiEvent.DismissModal
            else -> null
        }
    }

    private fun isUserInputGated(event: UiEvent): Boolean {
        if (state.value.mode != Mode.REPLAY) return false
        if (!state.value.player.loaded) return false
        return when (event) {
            UiEvent.RequestTapePick,
            is UiEvent.OnTapePicked,
            UiEvent.PlayerTogglePlay,
            UiEvent.PlayerPlay,
            UiEvent.PlayerPause,
            UiEvent.PlayerStepFwd,
            UiEvent.PlayerStepBack,
            is UiEvent.PlayerSeek,
            UiEvent.PlayerStop,

            // Always allow dismissing transient UI while replay is active.
            UiEvent.DismissModal,
            UiEvent.DismissClearBufferDialog,
            UiEvent.DismissDeleteFileDialog -> false

            else -> true
        }
    }

    private fun dismissModal() {
        _state.update {
            it.copy(
                showPokemonModal = false,
                showGenericModal = false
            )
        }
    }

    private suspend fun fetchRandomPokemonInternal(): PokemonDetail {
        val randomId = nextIntRecorded(1, 1026)
        val response = network.get("https://pokeapi.co/api/v2/pokemon/$randomId").requireSuccess()
        return Json { ignoreUnknownKeys = true }.decodeFromString<PokemonDetail>(response.body)
    }

    private suspend fun fetchData(url: String): String {
        return network.get(url).requireSuccess().body
    }

    private suspend fun postData(url: String, jsonBody: String): String {
        return network.post(url = url, body = jsonBody, contentType = "application/json")
            .requireSuccess()
            .body
    }

    private suspend fun fetchFromApiInternal(apiType: ApiType): String {
        return when (apiType) {
            is ApiType.Pokemon -> {
                val id = apiType.id ?: nextIntRecorded(1, 1026)
                fetchData("https://pokeapi.co/api/v2/pokemon/$id")
            }

            is ApiType.GitHub -> {
                fetchData("https://api.github.com/users/${apiType.username}")
            }

            is ApiType.JsonPlaceholder -> {
                val id = apiType.id ?: nextIntRecorded(1, 101)
                fetchData("https://jsonplaceholder.typicode.com/${apiType.endpoint}/$id")
            }

            is ApiType.Custom -> {
                fetchData(apiType.url)
            }
        }
    }

    companion object {
        fun factory(app: VcrApp): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val clock = app.sessionKit.clock()
                    val random = app.sessionKit.random()
                    val network = app.sessionKit.networkClient()
                    return ApiTestViewModel(app, clock, random, network) as T
                }
            }
        }
    }

    private fun recordUiEventIfNeeded(event: UiEvent) {
        if (state.value.mode != Mode.RECORD) return

        val eventName = when (event) {
            UiEvent.FetchRandomPokemon -> "FetchRandomPokemon"
            is UiEvent.FetchFromApi -> "FetchFromApi"
            UiEvent.ToggleMode -> "ToggleMode"
            UiEvent.ShareSessionLog -> "ShareSessionLog"
            UiEvent.DownloadSessionLog -> "DownloadSessionLog"
            UiEvent.ShowClearBufferDialog -> "ShowClearBufferDialog"
            UiEvent.DismissClearBufferDialog -> "DismissClearBufferDialog"
            UiEvent.ConfirmClearBuffer -> "ConfirmClearBuffer"
            UiEvent.ShowDeleteFileDialog -> "ShowDeleteFileDialog"
            UiEvent.DismissDeleteFileDialog -> "DismissDeleteFileDialog"
            UiEvent.ConfirmDeleteFile -> "ConfirmDeleteFile"
            UiEvent.RequestTapePick -> "RequestTapePick"
            is UiEvent.OnTapePicked -> "OnTapePicked"
            UiEvent.DismissModal -> "DismissModal"

            UiEvent.PlayerTogglePlay -> "PlayerTogglePlay"
            UiEvent.PlayerPlay -> "PlayerPlay"
            UiEvent.PlayerPause -> "PlayerPause"
            UiEvent.PlayerStepFwd -> "PlayerStepFwd"
            UiEvent.PlayerStepBack -> "PlayerStepBack"
            is UiEvent.PlayerSeek -> "PlayerSeek"
            UiEvent.PlayerStop -> "PlayerStop"
        }

        val payload: Map<String, JsonElement> = when (event) {
            is UiEvent.FetchFromApi -> mapOf("apiType" to JsonPrimitive(event.apiType::class.simpleName ?: "unknown"))
            is UiEvent.OnTapePicked -> mapOf("uri" to JsonPrimitive(event.uri?.toString()))
            is UiEvent.PlayerSeek -> mapOf("position" to JsonPrimitive(event.position))
            else -> emptyMap()
        }

        app.sessionKit.recordUiEvent(screen = "ApiTest", event = eventName, payload = payload)
    }

    private fun nextIntRecorded(from: Int, until: Int): Int {
        val value = random.nextInt(from, until)
        if (state.value.mode == Mode.RECORD) {
            app.sessionKit.recordAction(
                name = "ND_RANDOM_INT",
                details = buildJsonObject {
                    put("from", from)
                    put("until", until)
                    put("value", value)
                }
            )
        }
        return value
    }
}

data class ApiUiState(
    val mode: Mode,
    val content: ScreenContent = ScreenContent.Idle,
    val showPokemonModal: Boolean = false,
    val showGenericModal: Boolean = false,
    val showClearBufferDialog: Boolean = false,
    val showDeleteFileDialog: Boolean = false,
    val player: PlayerUiState = PlayerUiState()
)

data class PlayerUiState(
    val loaded: Boolean = false,
    val playing: Boolean = false,
    val position: Int = 0,
    val total: Int = 0
)

sealed class ScreenContent {
    data object Idle : ScreenContent()
    data object Loading : ScreenContent()
    data class PokemonLoaded(val pokemon: PokemonDetail) : ScreenContent()
    data class GenericLoaded(val response: String, val apiType: ApiType) : ScreenContent()
    data class Error(val message: String) : ScreenContent()
}

sealed class UiEvent {
    data object FetchRandomPokemon : UiEvent()
    data class FetchFromApi(val apiType: ApiType) : UiEvent()
    data object ToggleMode : UiEvent()
    data object ShareSessionLog : UiEvent()
    data object DownloadSessionLog : UiEvent()
    data object ShowClearBufferDialog : UiEvent()
    data object DismissClearBufferDialog : UiEvent()
    data object ConfirmClearBuffer : UiEvent()
    data object ShowDeleteFileDialog : UiEvent()
    data object DismissDeleteFileDialog : UiEvent()
    data object ConfirmDeleteFile : UiEvent()
    data object RequestTapePick : UiEvent()
    data class OnTapePicked(val uri: Uri?) : UiEvent()
    data object DismissModal : UiEvent()

    data object PlayerTogglePlay : UiEvent()
    data object PlayerPlay : UiEvent()
    data object PlayerPause : UiEvent()
    data object PlayerStepFwd : UiEvent()
    data object PlayerStepBack : UiEvent()
    data class PlayerSeek(val position: Int) : UiEvent()
    data object PlayerStop : UiEvent()
}

sealed class UiEffect {
    data class ShowToast(val message: String) : UiEffect()
    data object LaunchTapePicker : UiEffect()
}
