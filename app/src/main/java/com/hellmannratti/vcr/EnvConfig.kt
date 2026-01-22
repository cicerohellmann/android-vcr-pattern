package com.hellmannratti.vcr

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import com.hellmannratti.vcr.sessionkit.Mode
import com.hellmannratti.vcr.sessionkit.SessionKitConfig
import java.io.File

/**
 * Resolve the app's run configuration.
 * Defaults to RECORD mode with runtime switching capability.
 */
object EnvConfig {
    fun resolve(context: Context): SessionKitConfig {
        // Default to RECORD mode since we no longer have build flavors
        val runMode = Mode.RECORD
        
        // Default tape file path
        val tapeFile = File(context.filesDir, "sessions/events.ndjson")
        
        return SessionKitConfig(
            mode = runMode,
            tapeFile = tapeFile,
            appVersion = "1.0",
            device = android.os.Build.MODEL ?: "unknown"
        )
    }
}
