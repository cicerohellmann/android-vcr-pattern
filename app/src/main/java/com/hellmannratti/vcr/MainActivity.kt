package com.hellmannratti.vcr

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import coil.compose.AsyncImage
import com.hellmannratti.vcr.sessionkit.Mode
import com.hellmannratti.vcr.ui.theme.VcrTheme
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val app by lazy { VcrApp.from(this) }
    private val viewModel by lazy {
        ViewModelProvider(
            this,
            ApiTestViewModel.factory(app)
        )[ApiTestViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VcrTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ApiTestScreen(
                        modifier = Modifier.padding(innerPadding),
                        viewModel = viewModel
                    )
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
private fun ApiTestScreen(
    modifier: Modifier = Modifier,
    viewModel: ApiTestViewModel
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        viewModel.onEvent(UiEvent.OnTapePicked(uri))
    }

    LaunchedEffect(viewModel.effects) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is UiEffect.ShowToast -> Toast.makeText(context, effect.message, Toast.LENGTH_LONG).show()
                UiEffect.LaunchTapePicker -> filePickerLauncher.launch("*/*")
            }
        }
    }

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Mode indicator and toggle button
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when (state.mode) {
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
                    text = "Current Mode: ${state.mode.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        viewModel.onEvent(UiEvent.ToggleMode)
                    }
                ) {
                    Text(
                        when (state.mode) {
                            Mode.RECORD -> "Switch to REPLAY"
                            Mode.REPLAY -> "Switch to RECORD"
                            Mode.PASSTHROUGH -> "Switch to RECORD"
                        }
                    )
                }
            }
        }

        when (val content = state.content) {
            ScreenContent.Idle -> Text("Tap the button to pick a random Pokémon!")
            ScreenContent.Loading -> Text("Loading…")
            is ScreenContent.PokemonLoaded -> Text("Pokémon loaded! Click to view details.")
            is ScreenContent.GenericLoaded -> Text("API response loaded! Click to view details.")
            is ScreenContent.Error -> Text("Error: ${content.message}")
        }

        Button(
            onClick = { viewModel.onEvent(UiEvent.FetchRandomPokemon) }
        ) { Text("Pick Random Pokémon") }

        // GitHub API Button
        Button(
            onClick = {
                viewModel.onEvent(UiEvent.FetchFromApi(ApiType.GitHub("torvalds")))
            }
        ) { Text("Fetch GitHub User (torvalds)") }

        // JSONPlaceholder API Button
        Button(
            onClick = {
                viewModel.onEvent(UiEvent.FetchFromApi(ApiType.JsonPlaceholder("posts")))
            }
        ) { Text("Fetch JSONPlaceholder Post") }

        // Show export options when in RECORD mode
        if (state.mode == Mode.RECORD) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { viewModel.onEvent(UiEvent.ShareSessionLog) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Share")
                }
                Button(
                    onClick = { viewModel.onEvent(UiEvent.DownloadSessionLog) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Download")
                }
            }

            // Clear Buffer button
            Button(
                onClick = { viewModel.onEvent(UiEvent.ShowClearBufferDialog) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Clear Buffer")
            }

            // Delete Session File button with confirmation dialog
            Button(
                onClick = { viewModel.onEvent(UiEvent.ShowDeleteFileDialog) },
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete Session File")
            }
        }

        // Show file picker when in REPLAY mode
        if (state.mode == Mode.REPLAY) {
            Button(
                onClick = { viewModel.onEvent(UiEvent.RequestTapePick) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Load Tape from Downloads")
            }

            if (state.player.loaded) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Player: ${state.player.position}/${state.player.total}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.onEvent(UiEvent.PlayerStepBack) },
                                modifier = Modifier.weight(1f)
                            ) { Text("Step Back") }

                            Button(
                                onClick = { viewModel.onEvent(UiEvent.PlayerTogglePlay) },
                                modifier = Modifier.weight(1f)
                            ) { Text(if (state.player.playing) "Pause" else "Play") }

                            Button(
                                onClick = { viewModel.onEvent(UiEvent.PlayerStepFwd) },
                                modifier = Modifier.weight(1f)
                            ) { Text("Step Fwd") }
                        }

                        Slider(
                            value = state.player.position.toFloat(),
                            onValueChange = { v ->
                                viewModel.onEvent(UiEvent.PlayerSeek(v.roundToInt()))
                            },
                            valueRange = 0f..state.player.total.toFloat(),
                            steps = 0
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.onEvent(UiEvent.PlayerSeek(0)) },
                                modifier = Modifier.weight(1f)
                            ) { Text("Seek Start") }

                            Button(
                                onClick = { viewModel.onEvent(UiEvent.PlayerSeek(state.player.total)) },
                                modifier = Modifier.weight(1f)
                            ) { Text("Seek End") }
                        }
                    }
                }
            }
        }
    }

    // Clear Buffer confirmation dialog
    if (state.showClearBufferDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(UiEvent.DismissClearBufferDialog) },
            title = { Text("Clear Buffer?") },
            text = { Text("This will clear any pending writes in the buffer without deleting the session file.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onEvent(UiEvent.ConfirmClearBuffer)
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onEvent(UiEvent.DismissClearBufferDialog) }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete File confirmation dialog
    if (state.showDeleteFileDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(UiEvent.DismissDeleteFileDialog) },
            title = { Text("Delete Session File?") },
            text = { Text("This will permanently delete all recorded session data. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onEvent(UiEvent.ConfirmDeleteFile)
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onEvent(UiEvent.DismissDeleteFileDialog) }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Show modal when a Pokémon is loaded
    if (state.showPokemonModal) {
        val content = state.content
        if (content is ScreenContent.PokemonLoaded) {
            PokemonModal(
                pokemon = content.pokemon,
                onDismiss = { viewModel.onEvent(UiEvent.DismissModal) }
            )
        }
    }

    // Show modal when generic API response is loaded
    if (state.showGenericModal) {
        val content = state.content
        if (content is ScreenContent.GenericLoaded) {
            GenericApiModal(
                response = content.response,
                apiType = content.apiType,
                onDismiss = { viewModel.onEvent(UiEvent.DismissModal) }
            )
        }
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
