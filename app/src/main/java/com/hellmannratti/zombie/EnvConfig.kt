package com.hellmannratti.zombie

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import com.hellmannratti.zombie.replay.AppConfig
import com.hellmannratti.zombie.replay.Mode
import java.io.File

/**
 * Resolve the app's run configuration.
 * Defaults to RECORD mode with runtime switching capability.
 */
object EnvConfig {
    fun resolve(context: Context): AppConfig {
        // Default to RECORD mode since we no longer have build flavors
        val runMode = Mode.RECORD
        
        // Default tape file path
        val tapeFile = File(context.filesDir, "sessions/session.ndjson")
        
        return AppConfig(mode = runMode, tapeFile = tapeFile)
    }
}
