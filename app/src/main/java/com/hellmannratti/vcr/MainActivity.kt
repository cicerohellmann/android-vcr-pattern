package com.hellmannratti.vcr

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.hellmannratti.vcr.replay.Mode
import com.hellmannratti.vcr.replay.TapeLoader
import com.hellmannratti.vcr.ui.theme.VcrTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class MainActivity : ComponentActivity() {
    val app by lazy { VcrApp.from(this) }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { loadTapeFromUri(it) }
    }

    fun pickTapeFile() {
        filePickerLauncher.launch("*/*")
    }

    private fun loadTapeFromUri(uri: Uri) {
        lifecycleScope.launch {
            try {
                // Copy the selected file to app's private storage
                val destFile = File(app.recorder.file().parentFile, "selected_tape.ndjson")

                withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                // Define URL patterns for Pokemon API
                val patterns = listOf(
                    com.hellmannratti.vcr.replay.UrlPattern.fromRetrofitStyle("https://pokeapi.co/api/v2/pokemon/{id}")
                )
                
                // Load the tape
                val tape = TapeLoader.loadLatestSession(destFile, patterns)

                // Recreate the HTTP client with the new tape
                val replayer = com.hellmannratti.vcr.replay.ReplayerInterceptor() { Mode.REPLAY }.apply {
                    this.tape = tape
                }

                // Update the app's HTTP client with the new replayer
                app.updateHttpClientWithTape(replayer)

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Tape loaded successfully: ${tape.uniqueRequestCount} unique requests",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Failed to load tape: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VcrTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ApiTestScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    suspend fun fetchRandomPokemon(): PokemonDetail {
        val randomId = app.random.nextInt(1, 1026) // Pokémon IDs from 1 to 1025
        val request = Request.Builder()
            .url("https://pokeapi.co/api/v2/pokemon/$randomId")
            .build()

        return withContext(Dispatchers.IO) {
            val response = app.okHttp.newCall(request).execute()
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body?.string() ?: error("Empty body")
            Json { ignoreUnknownKeys = true }.decodeFromString<PokemonDetail>(body)
        }
    }

    // Generic GET request
    suspend fun fetchData(url: String): String {
        val request = Request.Builder()
            .url(url)
            .build()
        
        return withContext(Dispatchers.IO) {
            val response = app.okHttp.newCall(request).execute()
            if (!response.isSuccessful) error("HTTP ${response.code}")
            response.body?.string() ?: error("Empty body")
        }
    }

    // Generic POST request
    suspend fun postData(url: String, jsonBody: String): String {
        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        return withContext(Dispatchers.IO) {
            val response = app.okHttp.newCall(request).execute()
            if (!response.isSuccessful) error("HTTP ${response.code}")
            response.body?.string() ?: error("Empty body")
        }
    }

    suspend fun fetchFromApi(apiType: ApiType): String {
        return when (apiType) {
            is ApiType.Pokemon -> {
                val id = apiType.id ?: app.random.nextInt(1, 1026)
                fetchData("https://pokeapi.co/api/v2/pokemon/$id")
            }
            is ApiType.GitHub -> {
                fetchData("https://api.github.com/users/${apiType.username}")
            }
            is ApiType.JsonPlaceholder -> {
                val id = apiType.id ?: app.random.nextInt(1, 101)
                fetchData("https://jsonplaceholder.typicode.com/${apiType.endpoint}/$id")
            }
            is ApiType.Custom -> {
                fetchData(apiType.url)
            }
        }
    }

    fun shareSessionLog() {
        val file = app.recorder.file()
        val uri: Uri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/x-ndjson"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share session log"))
    }

    fun downloadSessionLog() {
        lifecycleScope.launch {
            try {
                val sourceFile = app.recorder.file()
                val fileName = "vcr_session_${System.currentTimeMillis()}.ndjson"

                withContext(Dispatchers.IO) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // Android 10+ (API 29+): Use MediaStore with scoped storage
                        val contentValues = ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                            put(MediaStore.Downloads.MIME_TYPE, "application/x-ndjson")
                            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        }

                        val uri = contentResolver.insert(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            contentValues
                        )
                        uri?.let {
                            contentResolver.openOutputStream(it)?.use { outputStream ->
                                sourceFile.inputStream().use { inputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }
                        } ?: error("Failed to create file in Downloads")
                    } else {
                        // Android 9 and below: Use legacy approach
                        @Suppress("DEPRECATION")
                        val downloadsDir =
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        downloadsDir.mkdirs()
                        val destFile = java.io.File(downloadsDir, fileName)
                        sourceFile.copyTo(destFile, overwrite = true)
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Session log saved to Downloads/$fileName",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Failed to download: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     * Clear the buffer by recreating the SessionRecorder instance.
     * This clears any pending writes in the executor queue without deleting the file.
     */
    internal fun clearBuffer() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                app.clearBuffer()
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Buffer cleared",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Failed to clear buffer: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     * Delete the session log file.
     * Deletes the events.ndjson file and creates a new empty one.
     */
    internal fun deleteSessionFile() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val sessionFile = File(app.filesDir, "sessions/events.ndjson")
                if (sessionFile.exists()) {
                    sessionFile.delete()
                }
                // Recreate the sessions directory if needed
                sessionFile.parentFile?.mkdirs()
                // Create a new empty file
                sessionFile.createNewFile()
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Session file deleted",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Failed to delete session file: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}

// API Type Abstraction
sealed class ApiType {
    data class Pokemon(val id: Int? = null) : ApiType()
    data class GitHub(val username: String) : ApiType()
    data class JsonPlaceholder(val endpoint: String, val id: Int? = null) : ApiType()
    data class Custom(val url: String) : ApiType()
}

private sealed interface UiState {
    data object Idle : UiState
    data object Loading : UiState
    data class Loaded(val pokemon: PokemonDetail) : UiState
    data class LoadedGeneric(val response: String, val apiType: ApiType) : UiState
    data class Error(val message: String) : UiState
}

@Serializable
data class PokemonDetail(
    val id: Int,
    val name: String,
    val height: Int,
    val weight: Int,
    val sprites: Sprites,
    val types: List<TypeSlot>,
    val abilities: List<AbilitySlot>
)

@Serializable
data class Sprites(
    @SerialName("front_default")
    val frontDefault: String?
)

@Serializable
data class TypeSlot(
    val type: TypeInfo
)

@Serializable
data class TypeInfo(
    val name: String
)

@Serializable
data class AbilitySlot(
    val ability: AbilityInfo
)

@Serializable
data class AbilityInfo(
    val name: String
)

// Generic API response wrapper
@Serializable
data class ApiResponse(
    val raw: String,  // Raw JSON string
    val parsed: Map<String, String>? = null  // Optional parsed key-value pairs
)

@Composable
private fun ApiTestScreen(modifier: Modifier = Modifier) {
    val activity = LocalContext.current as MainActivity
    var state by remember { mutableStateOf<UiState>(UiState.Idle) }
    var showModal by remember { mutableStateOf(false) }
    val currentMode by activity.app.currentMode.collectAsState()
    var showClearBufferDialog by remember { mutableStateOf(false) }
    var showDeleteFileDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Mode indicator and toggle button
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when (currentMode) {
                    Mode.RECORD -> MaterialTheme.colorScheme.primaryContainer
                    Mode.REPLAY -> MaterialTheme.colorScheme.secondaryContainer
                    Mode.PASSTHROUGH -> MaterialTheme.colorScheme.tertiaryContainer
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Current Mode: ${currentMode.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val newMode = when (currentMode) {
                            Mode.RECORD -> Mode.REPLAY
                            Mode.REPLAY -> Mode.RECORD
                            Mode.PASSTHROUGH -> Mode.RECORD
                        }
                        activity.app.switchMode(newMode)
                        Toast.makeText(
                            activity,
                            "Switched to ${newMode.name} mode",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                ) {
                    Text(
                        when (currentMode) {
                            Mode.RECORD -> "Switch to REPLAY"
                            Mode.REPLAY -> "Switch to RECORD"
                            Mode.PASSTHROUGH -> "Switch to RECORD"
                        }
                    )
                }
            }
        }

        when (val s = state) {
            UiState.Idle -> Text("Tap the button to pick a random Pokémon!")
            UiState.Loading -> Text("Loading…")
            is UiState.Loaded -> Text("Pokémon loaded! Click to view details.")
            is UiState.LoadedGeneric -> Text("API response loaded! Click to view details.")
            is UiState.Error -> Text("Error: ${s.message}")
        }

        Button(onClick = {
            state = UiState.Loading
            showModal = false
            
            // Launch the network call tied to the Activity lifecycle
            activity.lifecycleScope.launch {
                runCatching { activity.fetchRandomPokemon() }
                    .onSuccess { pokemon ->
                        state = UiState.Loaded(pokemon)
                        showModal = true
                    }
                    .onFailure { t ->
                        state = UiState.Error(t.message ?: "Unknown error")
                    }
            }
        }) { Text("Pick Random Pokémon") }

        // GitHub API Button
        Button(onClick = {
            state = UiState.Loading
            showModal = false
            activity.lifecycleScope.launch {
                val apiType = ApiType.GitHub("torvalds")
                runCatching { 
                    activity.fetchFromApi(apiType)
                }
                    .onSuccess { response ->
                        state = UiState.LoadedGeneric(response, apiType)
                        showModal = true
                    }
                    .onFailure { t ->
                        state = UiState.Error(t.message ?: "Unknown error")
                    }
            }
        }) { Text("Fetch GitHub User (torvalds)") }

        // JSONPlaceholder API Button
        Button(onClick = {
            state = UiState.Loading
            showModal = false
            activity.lifecycleScope.launch {
                val apiType = ApiType.JsonPlaceholder("posts")
                runCatching { 
                    activity.fetchFromApi(apiType)
                }
                    .onSuccess { response ->
                        state = UiState.LoadedGeneric(response, apiType)
                        showModal = true
                    }
                    .onFailure { t ->
                        state = UiState.Error(t.message ?: "Unknown error")
                    }
            }
        }) { Text("Fetch JSONPlaceholder Post") }

        // Show export options when in RECORD mode
        if (currentMode == Mode.RECORD) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { activity.shareSessionLog() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Share")
                }
                Button(
                    onClick = { activity.downloadSessionLog() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Download")
                }
            }
            
            // Clear Buffer button
            Button(
                onClick = { showClearBufferDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Clear Buffer")
            }
            
            // Delete Session File button with confirmation dialog
            Button(
                onClick = { showDeleteFileDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete Session File")
            }
        }

        // Show file picker when in REPLAY mode
        if (currentMode == Mode.REPLAY) {
            Button(
                onClick = { activity.pickTapeFile() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Load Tape from Downloads")
            }
        }
    }

    // Clear Buffer confirmation dialog
    if (showClearBufferDialog) {
        AlertDialog(
            onDismissRequest = { showClearBufferDialog = false },
            title = { Text("Clear Buffer?") },
            text = { Text("This will clear any pending writes in the buffer without deleting the session file.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        activity.clearBuffer()
                        showClearBufferDialog = false
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearBufferDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete File confirmation dialog
    if (showDeleteFileDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteFileDialog = false },
            title = { Text("Delete Session File?") },
            text = { Text("This will permanently delete all recorded session data. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        activity.deleteSessionFile()
                        showDeleteFileDialog = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteFileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Show modal when a Pokémon is loaded
    if (showModal && state is UiState.Loaded) {
        PokemonModal(
            pokemon = (state as UiState.Loaded).pokemon,
            onDismiss = { showModal = false }
        )
    }
    
    // Show modal when generic API response is loaded
    if (showModal && state is UiState.LoadedGeneric) {
        GenericApiModal(
            response = (state as UiState.LoadedGeneric).response,
            apiType = (state as UiState.LoadedGeneric).apiType,
            onDismiss = { showModal = false }
        )
    }
}

@Composable
private fun PokemonModal(pokemon: PokemonDetail, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        title = {
            Text(
                text = pokemon.name.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Pokémon Image
                pokemon.sprites.frontDefault?.let { imageUrl ->
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = "Image of ${pokemon.name}",
                        modifier = Modifier.size(150.dp)
                    )
                }

                // ID
                Text(
                    text = "#${pokemon.id}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary
                )

                // Types
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Types",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pokemon.types.forEach { typeSlot ->
                                Text(
                                    text = typeSlot.type.name.replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                // Physical Stats
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Physical Stats",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Height",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "${pokemon.height / 10.0} m",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Weight",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "${pokemon.weight / 10.0} kg",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Abilities
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Abilities",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        pokemon.abilities.forEach { abilitySlot ->
                            Text(
                                text = "• ${
                                    abilitySlot.ability.name.replace("-", " ")
                                        .replaceFirstChar { it.uppercase() }
                                }",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun GenericApiModal(response: String, apiType: ApiType, onDismiss: () -> Unit) {
    val title = when (apiType) {
        is ApiType.Pokemon -> "Pokemon API Response"
        is ApiType.GitHub -> "GitHub API Response"
        is ApiType.JsonPlaceholder -> "JSONPlaceholder API Response"
        is ApiType.Custom -> "Custom API Response"
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = response,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    VcrTheme {
        Text("Tap the button to pick a random Pokémon!")
    }
}