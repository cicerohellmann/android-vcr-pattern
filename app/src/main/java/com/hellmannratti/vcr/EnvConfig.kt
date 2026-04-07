package com.hellmannratti.vcr

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import com.hellmannratti.cassete.core.CasseteConfig
import com.hellmannratti.cassete.core.HeaderRedactor
import com.hellmannratti.cassete.core.MissingTapePolicy
import com.hellmannratti.cassete.core.Mode
import com.hellmannratti.cassete.core.UrlPattern
import java.io.File

/**
 * Resolve the app's run configuration.
 * Defaults to RECORD mode with runtime switching capability.
 */
object EnvConfig {
    fun endpoints(): ApiEndpoints = AppTestOverrides.apiEndpoints ?: ApiEndpoints()

    fun resolve(context: Context, apiEndpoints: ApiEndpoints = endpoints()): CasseteConfig {
        val runMode = Mode.RECORD
        val tapeFile = File(context.filesDir, "sessions/events.ndjson")

        return CasseteConfig(
            initialMode = runMode,
            tapeFile = tapeFile,
            appVersion = "1.0",
            device = android.os.Build.MODEL ?: "unknown",
            urlNormalizers = apiEndpoints.urlPatterns(),
            requestHeaderRedactor = HeaderRedactor.redactAuthTokens(),
            responseHeaderRedactor = HeaderRedactor.redactAuthTokens(),
            missingTapePolicy = MissingTapePolicy.PASSTHROUGH
        )
    }
}
