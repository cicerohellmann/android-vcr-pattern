package com.hellmannratti.vcr

import android.app.Application
import android.content.Context
import com.hellmannratti.cassete.core.Cassete
import com.hellmannratti.cassete.core.CasseteRuntime
import com.hellmannratti.cassete.core.KotlinRandomProvider
import com.hellmannratti.cassete.core.Mode
import com.hellmannratti.cassete.core.NetworkClient
import com.hellmannratti.cassete.core.RandomProvider
import com.hellmannratti.cassete.core.SystemClock
import com.hellmannratti.cassete.core.UuidGenerator
import com.hellmannratti.cassete.okhttp.CasseteOkHttp
import com.hellmannratti.cassete.okhttp.OkHttpNetworkClient
import com.hellmannratti.vcr.framework.AndroidTapeLogger
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient

/**
 * Holds a single Cassete controller and OkHttpClient for the whole app so
 * user actions and network events end up in the same NDJSON session file.
 */
class VcrApp : Application() {
    lateinit var cassete: CasseteRuntime
        private set

    lateinit var networkClient: NetworkClient
        private set

    lateinit var okHttpClient: OkHttpClient
        private set

    val replayController: ReplayController by lazy { ReplayController() }

    val clock = SystemClock
    val idGenerator = UuidGenerator
    val randomProvider: RandomProvider = KotlinRandomProvider(kotlin.random.Random.Default)
    val currentMode: StateFlow<Mode>
        get() = cassete.mode

    fun switchMode(newMode: Mode) {
        cassete.switchMode(newMode)
    }

    fun loadTapeFile(file: java.io.File): Int {
        return cassete.loadTape(file).uniqueRequestCount
    }

    fun clearBuffer() {
        cassete.clearBuffer()
    }

    override fun onCreate() {
        super.onCreate()
        val config = EnvConfig.resolve(this)
        cassete = Cassete.create(
            config = config,
            baseDir = filesDir,
            clock = clock,
            idGenerator = idGenerator,
            logger = AndroidTapeLogger()
        )
        okHttpClient = CasseteOkHttp.install(OkHttpClient.Builder(), cassete).build()
        networkClient = OkHttpNetworkClient { okHttpClient }
    }

    companion object {
        fun from(context: Context): VcrApp = context.applicationContext as VcrApp
    }
}
