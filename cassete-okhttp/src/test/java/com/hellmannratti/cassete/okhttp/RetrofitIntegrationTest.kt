package com.hellmannratti.cassete.okhttp

import com.hellmannratti.cassete.core.Cassete
import com.hellmannratti.cassete.core.CasseteConfig
import com.hellmannratti.cassete.core.Mode
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET

class RetrofitIntegrationTest {

    interface RetrofitApi {
        @GET("hello")
        fun hello(): Call<String>

        @GET("missing")
        fun missing(): Call<String>
    }

    @Test
    fun `retrofit reuses the same OkHttp client in record and replay modes`() {
        val baseDir = createTempDir(prefix = "cassete_retrofit_")
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("hello from retrofit")
                .addHeader("content-type", "text/plain")
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("nope")
                .addHeader("x-error-kind", "recorded")
        )
        server.start()

        try {
            val controller = Cassete.create(
                config = CasseteConfig(initialMode = Mode.RECORD),
                baseDir = baseDir
            )
            val okHttp = OkHttpClient.Builder()
                .addInterceptor(CasseteOkHttp.interceptor(controller))
                .build()
            val api = Retrofit.Builder()
                .baseUrl(server.url("/"))
                .client(okHttp)
                .addConverterFactory(ScalarsConverterFactory.create())
                .build()
                .create(RetrofitApi::class.java)

            val success = api.hello().execute()
            assertEquals(true, success.isSuccessful)
            assertEquals("hello from retrofit", success.body())

            val error = api.missing().execute()
            assertEquals(404, error.code())
            assertEquals("recorded", error.headers()["x-error-kind"])
            assertEquals("nope", error.errorBody()?.string())

            controller.switchMode(Mode.REPLAY)
            server.shutdown()

            val replayedSuccess = api.hello().execute()
            assertEquals(true, replayedSuccess.isSuccessful)
            assertEquals("hello from retrofit", replayedSuccess.body())

            val replayedError = api.missing().execute()
            assertEquals(404, replayedError.code())
            assertEquals("recorded", replayedError.headers()["x-error-kind"])
            assertEquals("nope", replayedError.errorBody()?.string())
        } finally {
            baseDir.deleteRecursively()
            runCatching { server.shutdown() }
        }
    }
}
