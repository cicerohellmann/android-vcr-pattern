package com.hellmannratti.vcr

import com.hellmannratti.cassete.core.Cassete
import com.hellmannratti.cassete.core.CasseteConfig
import com.hellmannratti.cassete.core.CasseteRuntime
import com.hellmannratti.cassete.core.Clock
import com.hellmannratti.cassete.core.FixedClock
import com.hellmannratti.cassete.core.IdGenerator
import com.hellmannratti.cassete.core.Mode
import com.hellmannratti.cassete.core.NetworkClient
import com.hellmannratti.cassete.core.NetworkResponse
import com.hellmannratti.cassete.core.RandomProvider
import com.hellmannratti.cassete.core.SequenceIdGenerator
import com.hellmannratti.cassete.core.TapeLogger
import com.hellmannratti.cassete.okhttp.CasseteOkHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createTempDirectory

@OptIn(ExperimentalCoroutinesApi::class)
class ApiTestViewModelReplayTest {

    @get:Rule
    val mainDispatcherRule: TestWatcher = MainDispatcherRule()

    @Test
    fun `player replay rebuilds identical UI state without live random or network`() = runTest {
        val tapeDir = createTempDirectory(prefix = "cassete_record_").toFile()
        val replayDir = createTempDirectory(prefix = "cassete_replay_").toFile()

        try {
            val recorded = recordPokemonSession(tapeDir) { advanceUntilIdle() }

            val replayBackend = PokemonBackendInterceptor()
            val replayClock = CountingClock(nowMsValue = 2_000L)
            val replayIds = CountingIdGenerator(ids = ArrayDeque(listOf("live-id-should-not-be-used")))
            val replayRandom = CountingRandomProvider(values = ArrayDeque(listOf(999)))
            val replayApp = createApp(
                cassete = createCassete(
                    baseDir = replayDir,
                    initialMode = Mode.REPLAY,
                    tapeFile = recorded.tapeFile
                )
            )
            val replayViewModel = ApiTestViewModel(
                app = replayApp,
                clock = replayClock,
                idGenerator = replayIds,
                random = replayRandom,
                network = OkHttpTestNetworkClient(
                    client = OkHttpClient.Builder()
                        .addInterceptor(CasseteOkHttp.interceptor(replayApp.cassete))
                        .addInterceptor(replayBackend)
                        .build()
                )
            )

            replayApp.loadTapeFile(recorded.tapeFile)
            replayApp.replayController.loadLatestSession(recorded.tapeFile)
            advanceUntilIdle()

            replayViewModel.onEvent(UiEvent.PlayerStepFwd)
            advanceUntilIdle()

            val replayed = replayViewModel.state.value.content as ScreenContent.PokemonLoaded
            assertEquals(recorded.pokemon, replayed.pokemon)
            assertEquals(recorded.requestId, replayViewModel.state.value.lastCompletedRequestId)
            assertEquals(recorded.completedAtMs, replayViewModel.state.value.lastUpdatedAtMs)
            assertEquals(0, replayBackend.calls.get())
            assertEquals(0, replayClock.nowMsCalls)
            assertEquals(0, replayIds.calls)
            assertEquals(0, replayRandom.calls)
            assertEquals(1, replayViewModel.state.value.player.position)
        } finally {
            tapeDir.deleteRecursively()
            replayDir.deleteRecursively()
        }
    }

    @Test
    fun `manual user input is gated while replay player is active`() = runTest {
        val tapeDir = createTempDirectory(prefix = "cassete_gate_record_").toFile()
        val replayDir = createTempDirectory(prefix = "cassete_gate_replay_").toFile()

        try {
            val recorded = recordPokemonSession(tapeDir) { advanceUntilIdle() }

            val replayBackend = PokemonBackendInterceptor()
            val replayIds = CountingIdGenerator(ids = ArrayDeque(listOf("live-id-should-not-be-used")))
            val replayRandom = CountingRandomProvider(values = ArrayDeque(listOf(321)))
            val replayApp = createApp(
                cassete = createCassete(
                    baseDir = replayDir,
                    initialMode = Mode.REPLAY,
                    tapeFile = recorded.tapeFile
                )
            )
            val replayViewModel = ApiTestViewModel(
                app = replayApp,
                clock = CountingClock(nowMsValue = 3_000L),
                idGenerator = replayIds,
                random = replayRandom,
                network = OkHttpTestNetworkClient(
                    client = OkHttpClient.Builder()
                        .addInterceptor(CasseteOkHttp.interceptor(replayApp.cassete))
                        .addInterceptor(replayBackend)
                        .build()
                )
            )

            val effects = mutableListOf<UiEffect>()
            val effectJob = launch { replayViewModel.effects.collect { effects += it } }

            replayApp.loadTapeFile(recorded.tapeFile)
            replayApp.replayController.loadLatestSession(recorded.tapeFile)
            advanceUntilIdle()

            replayViewModel.onEvent(UiEvent.FetchRandomPokemon)
            advanceUntilIdle()

            assertTrue(effects.filterIsInstance<UiEffect.ShowToast>().any {
                it.message.contains("gated", ignoreCase = true)
            })
            assertTrue(replayViewModel.state.value.content is ScreenContent.Idle)
            assertEquals(0, replayBackend.calls.get())
            assertEquals(0, replayRandom.calls)

            effectJob.cancel()
        } finally {
            tapeDir.deleteRecursively()
            replayDir.deleteRecursively()
        }
    }

    @Test
    fun `step back resets replay state so the same event can be replayed again`() = runTest {
        val tapeDir = createTempDirectory(prefix = "cassete_rewind_record_").toFile()
        val replayDir = createTempDirectory(prefix = "cassete_rewind_replay_").toFile()

        try {
            val recorded = recordPokemonSession(tapeDir) { advanceUntilIdle() }

            val replayBackend = PokemonBackendInterceptor()
            val replayIds = CountingIdGenerator(ids = ArrayDeque(listOf("live-id-should-not-be-used")))
            val replayRandom = CountingRandomProvider(values = ArrayDeque(listOf(777)))
            val replayApp = createApp(
                cassete = createCassete(
                    baseDir = replayDir,
                    initialMode = Mode.REPLAY,
                    tapeFile = recorded.tapeFile
                )
            )
            val replayViewModel = ApiTestViewModel(
                app = replayApp,
                clock = CountingClock(nowMsValue = 4_000L),
                idGenerator = replayIds,
                random = replayRandom,
                network = OkHttpTestNetworkClient(
                    client = OkHttpClient.Builder()
                        .addInterceptor(CasseteOkHttp.interceptor(replayApp.cassete))
                        .addInterceptor(replayBackend)
                        .build()
                )
            )

            replayApp.loadTapeFile(recorded.tapeFile)
            replayApp.replayController.loadLatestSession(recorded.tapeFile)
            advanceUntilIdle()

            replayViewModel.onEvent(UiEvent.PlayerStepFwd)
            advanceUntilIdle()
            val firstReplay = replayViewModel.state.value.content as ScreenContent.PokemonLoaded

            replayViewModel.onEvent(UiEvent.PlayerStepBack)
            advanceUntilIdle()
            assertTrue(replayViewModel.state.value.content is ScreenContent.Idle)
            assertEquals(null, replayViewModel.state.value.lastCompletedRequestId)
            assertEquals(null, replayViewModel.state.value.lastUpdatedAtMs)
            assertEquals(0, replayViewModel.state.value.player.position)

            replayViewModel.onEvent(UiEvent.PlayerStepFwd)
            advanceUntilIdle()
            val secondReplay = replayViewModel.state.value.content as ScreenContent.PokemonLoaded

            assertEquals(recorded.pokemon, firstReplay.pokemon)
            assertEquals(recorded.pokemon, secondReplay.pokemon)
            assertEquals(recorded.requestId, replayViewModel.state.value.lastCompletedRequestId)
            assertEquals(recorded.completedAtMs, replayViewModel.state.value.lastUpdatedAtMs)
            assertEquals(0, replayBackend.calls.get())
            assertEquals(0, replayIds.calls)
            assertEquals(0, replayRandom.calls)
            assertEquals(1, replayViewModel.state.value.player.position)
        } finally {
            tapeDir.deleteRecursively()
            replayDir.deleteRecursively()
        }
    }

    private fun recordPokemonSession(baseDir: File, advance: () -> Unit): RecordedSession {
        val backend = PokemonBackendInterceptor()
        val clock = FixedClock(1_000L)
        val ids = SequenceIdGenerator(ArrayDeque(listOf("vm-req-1")))
        val random = CountingRandomProvider(values = ArrayDeque(listOf(25)))
        val app = createApp(cassete = createCassete(baseDir = baseDir, initialMode = Mode.RECORD))
        val viewModel = ApiTestViewModel(
            app = app,
            clock = clock,
            idGenerator = ids,
            random = random,
            network = OkHttpTestNetworkClient(
                client = OkHttpClient.Builder()
                    .addInterceptor(CasseteOkHttp.interceptor(app.cassete))
                    .addInterceptor(backend)
                    .build()
            )
        )

        viewModel.onEvent(UiEvent.FetchRandomPokemon)
        advance()

        val loaded = viewModel.state.value.content as ScreenContent.PokemonLoaded
        assertEquals(1, backend.calls.get())
        assertEquals(1, random.calls)

        return RecordedSession(
            tapeFile = app.cassete.recordingFile,
            pokemon = loaded.pokemon,
            requestId = requireNotNull(viewModel.state.value.lastCompletedRequestId),
            completedAtMs = requireNotNull(viewModel.state.value.lastUpdatedAtMs)
        )
    }

    private fun createCassete(
        baseDir: File,
        initialMode: Mode,
        tapeFile: File? = null
    ): CasseteRuntime {
        return Cassete.create(
            config = CasseteConfig(
                initialMode = initialMode,
                tapeFile = tapeFile,
                appVersion = "test",
                device = "test-device"
            ),
            baseDir = baseDir,
            clock = FixedClock(10_000L),
            idGenerator = SequenceIdGenerator(ArrayDeque(listOf("req-1", "req-2", "req-3"))),
            logger = TapeLogger.NoOp
        )
    }

    private fun createApp(cassete: CasseteRuntime): VcrApp {
        val app = VcrApp()
        val casseteField = VcrApp::class.java.getDeclaredField("cassete")
        casseteField.isAccessible = true
        casseteField.set(app, cassete)

        val endpointsField = VcrApp::class.java.getDeclaredField("apiEndpoints")
        endpointsField.isAccessible = true
        endpointsField.set(app, ApiEndpoints())
        return app
    }

    private data class RecordedSession(
        val tapeFile: File,
        val pokemon: PokemonDetail,
        val requestId: String,
        val completedAtMs: Long
    )

    private class OkHttpTestNetworkClient(
        private val client: OkHttpClient
    ) : NetworkClient {
        override suspend fun get(url: String, headers: Map<String, String>): NetworkResponse {
            val request = Request.Builder()
                .url(url)
                .get()
                .apply {
                    for ((name, value) in headers) {
                        addHeader(name, value)
                    }
                }
                .build()
            return execute(request)
        }

        override suspend fun post(
            url: String,
            body: String,
            contentType: String,
            headers: Map<String, String>
        ): NetworkResponse {
            val request = Request.Builder()
                .url(url)
                .post(body.toRequestBody(contentType.toMediaType()))
                .apply {
                    for ((name, value) in headers) {
                        addHeader(name, value)
                    }
                }
                .build()
            return execute(request)
        }

        private fun execute(request: Request): NetworkResponse {
            client.newCall(request).execute().use { response ->
                return NetworkResponse(
                    code = response.code,
                    headers = response.headers.toMultimap()
                        .mapValues { (_, values) -> values.joinToString(",") },
                    body = response.body?.string().orEmpty()
                )
            }
        }
    }

    private class CountingRandomProvider(
        private val values: ArrayDeque<Int>
    ) : RandomProvider {
        var calls: Int = 0
            private set

        override fun nextInt(from: Int, until: Int): Int {
            calls += 1
            val value = values.removeFirstOrNull() ?: error("No random value left for test")
            require(value in from until until) { "Value $value not in range [$from, $until)" }
            return value
        }
    }

    private class CountingClock(
        private val nowMsValue: Long,
        private val nowNsValue: Long = nowMsValue * 1_000_000
    ) : Clock {
        var nowMsCalls: Int = 0
            private set

        override fun nowMs(): Long {
            nowMsCalls += 1
            return nowMsValue
        }

        override fun nowNs(): Long = nowNsValue
    }

    private class CountingIdGenerator(
        private val ids: ArrayDeque<String>
    ) : IdGenerator {
        var calls: Int = 0
            private set

        override fun uuid(): String {
            calls += 1
            return ids.removeFirstOrNull() ?: error("No UUID left for test")
        }
    }

    private class PokemonBackendInterceptor : Interceptor {
        val calls = AtomicInteger(0)

        override fun intercept(chain: Interceptor.Chain): Response {
            calls.incrementAndGet()
            val request = chain.request()
            val pokemonId = request.url.pathSegments.last()
            val body = """
                {
                  "id": $pokemonId,
                  "name": "pokemon-$pokemonId",
                  "height": 4,
                  "weight": 60,
                  "sprites": {"front_default": "https://example.test/$pokemonId.png"},
                  "types": [{"type": {"name": "electric"}}],
                  "abilities": [{"ability": {"name": "static"}}]
                }
            """.trimIndent()

            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .addHeader("content-type", "application/json")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    private class MainDispatcherRule(
        private val dispatcher: TestDispatcher = StandardTestDispatcher()
    ) : TestWatcher() {
        override fun starting(description: Description) {
            Dispatchers.setMain(dispatcher)
        }

        override fun finished(description: Description) {
            Dispatchers.resetMain()
        }
    }
}
