package com.hellmannratti.vcr

import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlin.math.roundToInt

@Composable
fun PlayerControllerWindow(
    state: ApiUiState,
    onEvent: (UiEvent) -> Unit
) {
    if (state.mode != com.hellmannratti.cassete.core.Mode.REPLAY) return
    if (!state.player.loaded) return

    var sliderValue by remember { mutableFloatStateOf(state.player.position.toFloat()) }
    LaunchedEffect(state.player.position, state.player.total) {
        // Keep the slider in sync with the current player position when it changes externally
        // (e.g., play/step/seek).
        sliderValue = state.player.position
            .coerceIn(0, state.player.total)
            .toFloat()
    }

    Dialog(
        onDismissRequest = { /* non-dismissible */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        val view = LocalView.current
        DisposableEffect(Unit) {
            val window = (view.parent as? DialogWindowProvider)?.window
            if (window != null) {
                // IMPORTANT: Do NOT change WindowManager.LayoutParams.type here.
                // Compose's Dialog window is already added to WindowManager by the time this runs;
                // changing type after that will crash with:
                // IllegalArgumentException: Window type can not be changed after the window is added.
                val lp = window.attributes
                lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                lp.width = WindowManager.LayoutParams.MATCH_PARENT
                lp.height = WindowManager.LayoutParams.WRAP_CONTENT
                lp.dimAmount = 0f
                // Make it behave like a persistent controller rather than a modal.
                lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                window.attributes = lp
                window.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT
                )
            }
            onDispose { }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Replay: ${state.player.position}/${state.player.total}",
                    style = MaterialTheme.typography.titleSmall
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onEvent(UiEvent.PlayerStepBack) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Back")
                    }
                    Button(
                        onClick = { onEvent(UiEvent.PlayerTogglePlay) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (state.player.playing) "Pause" else "Play")
                    }
                    Button(
                        onClick = { onEvent(UiEvent.PlayerStepFwd) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Next")
                    }
                }

                Slider(
                    value = sliderValue,
                    onValueChange = { v ->
                        sliderValue = v
                    },
                    onValueChangeFinished = {
                        onEvent(UiEvent.PlayerSeek(sliderValue.roundToInt()))
                    },
                    valueRange = 0f..state.player.total.coerceAtLeast(0).toFloat(),
                    steps = 0
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onEvent(UiEvent.PlayerSeek(0)) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Start") }

                    Button(
                        onClick = { onEvent(UiEvent.PlayerSeek(state.player.total)) },
                        modifier = Modifier.weight(1f)
                    ) { Text("End") }

                    Button(
                        onClick = { onEvent(UiEvent.PlayerStop) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Stop") }
                }
            }
        }
    }
}
