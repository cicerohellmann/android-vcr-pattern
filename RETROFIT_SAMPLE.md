# Retrofit + Cassete Setup Guide

This guide demonstrates how to use Cassete with Retrofit for HTTP record/replay testing.

## Overview

Cassete integrates with Retrofit through its OkHttp adapter. Since Retrofit uses OkHttp under the hood, you can install the Cassete interceptor into your OkHttp client and get full record/replay capabilities with your existing Retrofit service interfaces.

## Basic Setup

### 1. Create a Cassete Controller

```kotlin
import com.hellmannratti.cassete.core.Cassete
import com.hellmannratti.cassete.core.CasseteConfig
import com.hellmannratti.cassete.core.Mode
import java.io.File

val casseteDir = File(cacheDir, "cassete")
val controller = Cassete.create(
    config = CasseteConfig(
        initialMode = Mode.RECORD  // or Mode.REPLAY
    ),
    baseDir = casseteDir
)
```

### 2. Build OkHttpClient with Cassete Interceptor

```kotlin
import com.hellmannratti.cassete.okhttp.CasseteOkHttp
import okhttp3.OkHttpClient

val okHttpClient = OkHttpClient.Builder()
    .addInterceptor(CasseteOkHttp.interceptor(controller))
    // Add any other interceptors, auth, TLS config, etc.
    .build()
```

### 3. Create Retrofit Instance

```kotlin
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

val retrofit = Retrofit.Builder()
    .baseUrl("https://api.example.com/")
    .client(okHttpClient)
    .addConverterFactory(GsonConverterFactory.create())
    .build()
```

### 4. Define Your Service Interface

```kotlin
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path

interface UserService {
    @GET("users/{id}")
    fun getUser(@Path("id") userId: String): Call<User>
}

data class User(
    val id: String,
    val name: String,
    val email: String
)
```

### 5. Use Your Service

```kotlin
val userService = retrofit.create(UserService::class.java)
val response = userService.getUser("123").execute()
if (response.isSuccessful) {
    val user = response.body()
    println("User: ${user?.name}")
}
```

## Record Mode

When initialized with `Mode.RECORD`, Cassete records all HTTP requests and responses to disk:

```kotlin
val controller = Cassete.create(
    config = CasseteConfig(initialMode = Mode.RECORD),
    baseDir = casseteDir
)
```

During record mode:
- All HTTP requests pass through to the real server
- Responses are captured and stored to disk as NDJSON
- The tape file is stored in `baseDir/sessions/<timestamp>.ndjson`

## Replay Mode

When initialized with `Mode.REPLAY`, Cassete plays back recorded responses without hitting the network:

```kotlin
val controller = Cassete.create(
    config = CasseteConfig(initialMode = Mode.REPLAY),
    baseDir = casseteDir
)
```

During replay mode:
- HTTP requests are matched against recorded tape entries
- Matching responses are returned from the tape
- Network access is not required

## Dynamic Mode Switching

Switch between modes at runtime using the controller:

```kotlin
// Switch to replay mode
controller.switchMode(Mode.REPLAY)

// Make requests using the same Retrofit service
val response = userService.getUser("123").execute()
// This uses the tape instead of the network

// Switch back to record mode
controller.switchMode(Mode.RECORD)
```

## Loading Specific Tapes

Load a previously recorded tape file:

```kotlin
val tapeFile = File(casseteDir, "my-tape.ndjson")
val tapeInfo = controller.loadTape(tapeFile)
println("Loaded tape with ${tapeInfo.uniqueRequestCount} unique requests")
println("Total responses: ${tapeInfo.totalResponses}")
```

## Resetting Replay Cursors

After replaying all responses for a request, reset the cursors to replay from the beginning:

```kotlin
controller.resetReplayCursors()
```

## Complete Example

```kotlin
import com.hellmannratti.cassete.core.Cassete
import com.hellmannratti.cassete.core.CasseteConfig
import com.hellmannratti.cassete.core.Mode
import com.hellmannratti.cassete.okhttp.CasseteOkHttp
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.io.File

// Service interface
interface GitHubApi {
    @GET("users/{username}")
    fun getUser(@Path("username") username: String): Call<GitHubUser>
}

data class GitHubUser(
    val login: String,
    val name: String?,
    val public_repos: Int
)

// Setup and usage
fun setupRetrofitWithCassete(cacheDir: File): GitHubApi {
    // Create Cassete controller
    val casseteDir = File(cacheDir, "cassete")
    val controller = Cassete.create(
        config = CasseteConfig(
            initialMode = Mode.RECORD
        ),
        baseDir = casseteDir
    )

    // Create OkHttp client with Cassete
    val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(CasseteOkHttp.interceptor(controller))
        .build()

    // Create Retrofit instance
    return Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(GitHubApi::class.java)
}

// Usage in a test or activity
fun main(cacheDir: File) {
    val api = setupRetrofitWithCassete(cacheDir)
    
    // Record mode: hits the real GitHub API
    val response = api.getUser("octocat").execute()
    if (response.isSuccessful) {
        println("User: ${response.body()?.name}")
    }
    
    // Tape is now recorded at cacheDir/cassete/sessions/<timestamp>.ndjson
}
```

## Handling Converters

Cassete works transparently with all Retrofit converters:

- **Gson**: `GsonConverterFactory.create()`
- **Moshi**: `MoshiConverterFactory.create()`
- **kotlinx.serialization**: `KotlinxSerializationConverterFactory`
- **Scalars**: `ScalarsConverterFactory.create()`

The replayed HTTP responses are reconstructed as real HTTP responses, so converters work exactly the same in replay mode.

## Advanced Configuration

### URL Normalization

Normalize dynamic URL segments (e.g., user IDs) so requests match regardless of parameter values:

```kotlin
import com.hellmannratti.cassete.core.UrlNormalizer

val normalizers = listOf(
    UrlNormalizer.fromRetrofitStyle(
        pattern = "https://api.example.com/users/{id}",
        baseUrl = "https://api.example.com/"
    )
)

val controller = Cassete.create(
    config = CasseteConfig(
        initialMode = Mode.RECORD,
        urlNormalizers = normalizers
    ),
    baseDir = casseteDir
)
```

### Header Redaction

Redact sensitive headers like authentication tokens from recorded tapes:

```kotlin
import com.hellmannratti.cassete.core.HeaderRedactor

val requestHeaderRedactor = HeaderRedactor.redactAuthTokens()

val controller = Cassete.create(
    config = CasseteConfig(
        initialMode = Mode.RECORD,
        requestHeaderRedactor = requestHeaderRedactor
    ),
    baseDir = casseteDir
)
```

### Body Redaction

Redact sensitive data from request and response bodies:

```kotlin
import com.hellmannratti.cassete.core.BodyRedactor

val requestBodyRedactor = BodyRedactor { _, body ->
    body?.replace(Regex(""""password":"[^"]*""""), """"password":"***"""")
}

val controller = Cassete.create(
    config = CasseteConfig(
        initialMode = Mode.RECORD,
        requestBodyRedactor = requestBodyRedactor
    ),
    baseDir = casseteDir
)
```

## Testing with Cassete and Retrofit

### Example: Unit Test

```kotlin
import org.junit.Test
import org.junit.Before
import org.junit.After
import java.io.File

class UserServiceTest {
    private lateinit var api: GitHubApi
    private lateinit var casseteDir: File

    @Before
    fun setUp() {
        casseteDir = File.createTempFile("cassete", "").apply { 
            delete()
            mkdirs()
        }
        api = setupRetrofitWithCassete(casseteDir)
    }

    @After
    fun tearDown() {
        casseteDir.deleteRecursively()
    }

    @Test
    fun testGetUserSucceeds() {
        val response = api.getUser("octocat").execute()
        assert(response.isSuccessful)
        assert(response.body()?.login == "octocat")
    }
}
```

## Key Points

1. **No Code Changes**: Use the same Retrofit service interfaces in record and replay modes
2. **Transparent**: Cassete works as an OkHttp interceptor, invisible to Retrofit
3. **Mode Switching**: Switch between record/replay without restarting the app
4. **Deterministic**: Responses are replayed in order, matching by method + URL + optional body hash
5. **Configurable**: Redaction, URL normalization, and replay miss policies are customizable

## Troubleshooting

### Tape Not Found in Replay Mode

If you get a `NoTapeFoundException`, check:
- The tape file path is correct
- The mode is actually `Mode.REPLAY`
- Request matching: method, URL, and body hash must match recorded requests

### Requests Not Matching

Use URL normalizers to account for dynamic segments:
- User IDs: `/users/{id}`
- Timestamps: `/events?after=2024-01-01`
- UUIDs: anywhere in the path or query

### Response Headers or Body Different in Replay

By default, Cassete:
- Strips `content-encoding` and `content-length` headers (transport artifacts)
- Preserves all request and response bodies
- Preserves auth headers (use `HeaderRedactor.redactAuthTokens()` to strip them)

Configure `CasseteConfig.ignoredResponseHeaders` to customize which headers are stripped.
