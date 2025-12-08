package com.hellmannratti.zombie.replay

import android.content.Context
import com.hellmannratti.zombie.ZombieApp
import com.hellmannratti.zombie.framework.AndroidTapeLogger
import okhttp3.OkHttpClient

/**
 * Minimal OkHttp client wiring RecordingInterceptor as the outermost interceptor.
 */
object HttpClients {
    fun recording(context: Context): OkHttpClient {
        val recorder = ZombieApp.from(context).recorder
        return OkHttpClient.Builder()
            .addInterceptor(RecordingInterceptor(recorder) { Mode.RECORD })
            .build()
    }

    /**
     * Step 8: Wiring example — full client with Recording + Replayer and mode switching.
     *
     * Interceptor order matters: Recording first (outermost), Replayer second.
     */
    fun client(context: Context, config: AppConfig): OkHttpClient {
        val recorder = ZombieApp.from(context).recorder

        return when (config.mode) {
            Mode.PASSTHROUGH -> {
                // Plain client: no recording, no replay
                OkHttpClient.Builder().build()
            }
            Mode.RECORD -> {
                OkHttpClient.Builder()
                    .addInterceptor(RecordingInterceptor(recorder) { config.mode })
                    .build()
            }
            Mode.REPLAY -> {
                val app = ZombieApp.from(context)
                val file = config.tapeFile ?: app.recorder.file()
                val logger = AndroidTapeLogger()
                
                try {
                    // Define URL patterns for multiple APIs
                    val patterns = listOf(
                        // Pokemon API
                        UrlPattern.fromRetrofitStyle("https://pokeapi.co/api/v2/pokemon/{id}"),
                        
                        // GitHub API
                        UrlPattern.fromRetrofitStyle("https://api.github.com/users/{name}"),
                        UrlPattern.fromRetrofitStyle("https://api.github.com/repos/{name}/{name}"),
                        
                        // JSONPlaceholder (testing API)
                        UrlPattern.fromRetrofitStyle("https://jsonplaceholder.typicode.com/posts/{id}"),
                        UrlPattern.fromRetrofitStyle("https://jsonplaceholder.typicode.com/users/{id}")
                    )
                    
                    val replayer = ReplayerInterceptor(logger) { config.mode }.apply {
                        tape = TapeLoader.loadLatestSession(file, patterns, logger)
                    }
                    OkHttpClient.Builder()
                        .addInterceptor(replayer)
                        .build()
                } catch (e: NoTapeFoundException) {
                    logger.w("HttpClients", "No tape found, using passthrough mode: ${e.message}")
                    // Fall back to a plain client if tape doesn't exist
                    OkHttpClient.Builder().build()
                }
            }
        }
    }
}
