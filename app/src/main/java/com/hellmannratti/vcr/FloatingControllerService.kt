package com.hellmannratti.vcr

import android.app.PendingIntent
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.hellmannratti.cassete.core.SessionPlayer
import com.hellmannratti.vcr.ui.theme.VcrTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

class FloatingControllerService : Service() {

    private lateinit var wm: WindowManager
    private var rootView: View? = null
    private var params: WindowManager.LayoutParams? = null

    private val app by lazy { VcrApp.from(this) }
    private val viewTreeOwner = OverlayViewTreeOwner()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        viewTreeOwner.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                if (!Settings.canDrawOverlays(this)) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                viewTreeOwner.onStart()
                startForeground(NOTIF_ID, buildNotification())
                ensureOverlay()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        removeOverlay()
        viewTreeOwner.onDestroy()
        super.onDestroy()
    }

    private fun ensureOverlay() {
        if (rootView != null) return

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 200
        }
        params = lp

        val composeView = ComposeView(this).apply {
            // ComposeView inside a WindowManager overlay has no Activity/Fragment view tree.
            // Provide the required owners to avoid:
            // IllegalStateException: ViewTreeLifecycleOwner not found from ComposeView
            setViewTreeLifecycleOwner(viewTreeOwner)
            setViewTreeSavedStateRegistryOwner(viewTreeOwner)
            setViewTreeViewModelStoreOwner(viewTreeOwner)

            setContent {
                VcrTheme {
                    val playerState by app.replayController.player.state.collectAsState(
                        initial = SessionPlayer.PlayerState()
                    )
                    val scope = rememberCoroutineScope()

                    Card(
                        modifier = Modifier
                            .padding(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Drag handle (non-button) so button clicks/gestures remain reliable.
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(28.dp)
                                    .pointerInput(Unit) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consumeAllChanges()
                                            moveBy(
                                                dx = (-dragAmount.x).roundToInt(),
                                                dy = (dragAmount.y).roundToInt()
                                            )
                                        }
                                    }
                            ) {
                                Text(
                                    text = "Drag",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        scope.launch { app.replayController.stepBack() }
                                    },
                                    modifier = Modifier.weight(1f),
                                    enabled = playerState.loaded && playerState.position > 0
                                ) { Text("Back") }
                                Button(
                                    onClick = {
                                        scope.launch { app.replayController.stepForward() }
                                    },
                                    modifier = Modifier.weight(1f),
                                    enabled = playerState.loaded && playerState.position < playerState.total
                                ) { Text("Next") }
                            }
                        }
                    }
                }
            }
        }

        rootView = composeView
        wm.addView(composeView, lp)
    }

    private fun moveBy(dx: Int, dy: Int) {
        val v = rootView ?: return
        val lp = params ?: return
        lp.x += dx
        lp.y += dy
        wm.updateViewLayout(v, lp)
    }

    /**
     * Minimal view-tree owners for Compose hosted outside an Activity (WindowManager overlay).
     *
     * We keep the lifecycle in RESUMED while the service is running so Compose can create a
     * lifecycle-aware recomposer.
     */
    private class OverlayViewTreeOwner :
        LifecycleOwner,
        SavedStateRegistryOwner,
        ViewModelStoreOwner {

        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)
        private val store = ViewModelStore()

        override val lifecycle: Lifecycle
            get() = lifecycleRegistry

        override val savedStateRegistry = savedStateRegistryController.savedStateRegistry

        override val viewModelStore: ViewModelStore
            get() = store

        fun onCreate() {
            savedStateRegistryController.performAttach()
            savedStateRegistryController.performRestore(null)
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
        }

        fun onStart() {
            lifecycleRegistry.currentState = Lifecycle.State.STARTED
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }

        fun onDestroy() {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
            store.clear()
        }
    }

    private fun removeOverlay() {
        val v = rootView ?: return
        runCatching { wm.removeView(v) }
        rootView = null
        params = null
    }

    private fun buildNotification(): Notification {
        val channelId = NOTIF_CHANNEL
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Floating Controls", NotificationManager.IMPORTANCE_LOW)
            )
        }

        val stopIntent = Intent(this, FloatingControllerService::class.java).apply {
            action = ACTION_STOP
        }

        val stopPending = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("QA Floating Controls")
            .setContentText("Overlay controller is running")
            .setOngoing(true)
            .addAction(0, "Stop", stopPending)
            .build()
    }

    private class DragTouchListener(
        private val wm: WindowManager,
        private val params: WindowManager.LayoutParams
    ) : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            return when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (initialTouchX - event.rawX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    wm.updateViewLayout(v, params)
                    true
                }

                else -> false
            }
        }
    }

    companion object {
        private const val NOTIF_ID = 1001
        private const val NOTIF_CHANNEL = "floating_controls"
        private const val ACTION_START = "com.hellmannratti.vcr.action.FLOATING_CONTROLS_START"
        private const val ACTION_STOP = "com.hellmannratti.vcr.action.FLOATING_CONTROLS_STOP"

        fun start(context: Context) {
            val i = Intent(context, FloatingControllerService::class.java).apply { action = ACTION_START }
            ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            val i = Intent(context, FloatingControllerService::class.java).apply { action = ACTION_STOP }
            context.startService(i)
        }
    }
}
