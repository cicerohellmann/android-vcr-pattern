### android-vcr-pattern: HTTP Recording & Replay for Android

android-vcr-pattern is an Android application that implements the **VCR (Video Cassette Recorder) pattern** for HTTP network interactions. It allows you to record real API calls during development or testing, then replay them later without hitting the actual network.

---

### The VCR Pattern Concept

The VCR pattern is inspired by old video cassette recorders:

1. **Record Mode**: Like pressing "record" on a VCR, the app captures all HTTP requests and responses as they happen in real-time
2. **Replay Mode**: Like pressing "play" on a VCR, the app replays previously recorded responses without making actual network calls
3. **The Tape**: All recorded interactions are stored in a "tape" file (NDJSON format) that can be replayed later

**Why use this pattern?**
- **Deterministic testing**: Tests always get the same responses
- **Offline development**: Work without network connectivity
- **Faster tests**: No waiting for real API calls
- **Reproducible bugs**: Capture a problematic session and replay it exactly

---

### Project Architecture

This is a single-module Android application that implements the VCR pattern for HTTP recording and replay.

#### **`app` module** (Android application)
The application contains all the VCR pattern logic organized into packages:

**Core VCR Logic** (`com.hellmannratti.vcr.replay`):
- `Event.kt` - Event model (SESSION_START, ACTION, REQUEST, RESPONSE)
- `SessionRecorder.kt` - Writes events to NDJSON files
- `RecordingInterceptor.kt` - OkHttp interceptor that captures HTTP traffic
- `ReplayerInterceptor.kt` - OkHttp interceptor that serves recorded responses
- `TapeLoader.kt` - Loads and parses NDJSON tape files
- `ReplayTape.kt` - In-memory tape structure for replay lookups
- `UrlPattern.kt` - Pattern matching for dynamic URLs (e.g., `/users/{id}`)
- `Mode.kt` - Defines three modes: RECORD, REPLAY, PASSTHROUGH
- `TapeLogger.kt` - Logging interface for platform-agnostic code
- `NoTapeFoundException.kt` - Exception for missing tape files

**Android Integration** (`com.hellmannratti.vcr`):
- `VcrApp.kt` - Application class holding the SessionRecorder and OkHttpClient
- `MainActivity.kt` - UI for switching modes and triggering actions
- `EnvConfig.kt` - Configuration resolver for selecting mode at startup

**Framework Adapters** (`com.hellmannratti.vcr.framework`):
- `HttpClients.kt` - Wiring logic for building OkHttp clients in different modes
- `AndroidTapeLogger.kt` - Android-specific logger implementation

---

### How It Works: From Start to Finish

#### **Phase 1: Starting a Recording Session**

1. **App Launch** (`VcrApp.onCreate()`)
   ```kotlin
   // Resolve the mode (RECORD, REPLAY, or PASSTHROUGH)
   config = EnvConfig.resolve(this)
   
   // Create a SessionRecorder that writes to files/sessions/events.ndjson
   recorder = SessionRecorder(
       baseDir = filesDir,
       logSessionStart = config.mode != Mode.REPLAY,
       appVersion = "1.0",
       device = android.os.Build.MODEL
   )
   ```

2. **Session Start Event** (`SessionRecorder.init`)
    - When created in RECORD mode, the recorder immediately writes a `SESSION_START` event:
   ```json
   {"type":"SESSION_START","ts":1733420800000,"appVersion":"1.0","device":"Pixel 6"}
   ```
    - This marks the beginning of a new recording session

3. **HTTP Client Setup** (`HttpClients.client()`)
    - In **RECORD mode**: Creates an OkHttpClient with `RecordingInterceptor`
    - In **REPLAY mode**: Creates an OkHttpClient with `ReplayerInterceptor` (loads existing tape)
    - In **PASSTHROUGH mode**: Creates a plain OkHttpClient (no recording/replay)

#### **Phase 2: Recording HTTP Traffic (RECORD Mode)**

When the app makes an HTTP request in RECORD mode:

1. **Request Interception** (`RecordingInterceptor.intercept()`)
   ```kotlin
   // Generate unique ID for this request
   val requestId = UUID.randomUUID().toString()
   
   // Hash the request body (if present)
   val bodyHash = safeBodySha256(request.body)
   
   // Log REQUEST event
   recorder.log(RequestEvent(
       ts = System.currentTimeMillis(),
       requestId = requestId,
       method = "GET",
       url = "https://pokeapi.co/api/v2/pokemon/25",
       bodySha256 = bodyHash
   ))
   ```

2. **Network Call Execution**
    - The interceptor proceeds with the actual network call
    - Measures the duration

3. **Response Recording**
   ```kotlin
   // Read response body
   val bodyString = response.body?.string() ?: ""
   
   // Log RESPONSE event
   recorder.log(ResponseEvent(
       ts = System.currentTimeMillis(),
       requestId = requestId,
       code = 200,
       headers = sanitizedHeaders,
       body = bodyString,
       durationMs = tookMs
   ))
   ```

4. **NDJSON File Output** (`SessionRecorder.log()`)
    - Events are written to `files/sessions/events.ndjson` as newline-delimited JSON (each line is a separate JSON object):
   ```
   {"type":"REQUEST","ts":1733420801000,"requestId":"abc-123","method":"GET","url":"https://pokeapi.co/api/v2/pokemon/25","bodySha256":null}
   {"type":"RESPONSE","ts":1733420801250,"requestId":"abc-123","code":200,"headers":{"content-type":"application/json"},"body":"{\"name\":\"pikachu\",\"id\":25}","durationMs":250}
   ```

5. **User Actions** (Optional)
    - The app can also log user interactions:
   ```kotlin
   recorder.log(ActionEvent(
       ts = System.currentTimeMillis(),
       name = "BUTTON_CLICK",
       details = mapOf("button" to JsonPrimitive("fetch_pokemon"))
   ))
   ```

#### **Phase 3: Replaying a Session (REPLAY Mode)**

To replay a recorded session:

1. **Load the Tape** (`TapeLoader.loadLatestSession()`)
   ```kotlin
   // Define URL patterns for APIs you want to replay
   val patterns = listOf(
       UrlPattern.fromRetrofitStyle("https://pokeapi.co/api/v2/pokemon/{id}"),
       UrlPattern.fromRetrofitStyle("https://api.github.com/users/{name}")
   )
   
   // Load the tape file and parse it
   val tape = TapeLoader.loadLatestSession(file, patterns, logger)
   ```

2. **Tape Structure** (`ReplayTape` class)
    - The tape is built from the NDJSON file by pairing REQUEST and RESPONSE events
    - Internally maintains a map of request keys to queued responses
    - Supports URL pattern matching for dynamic URLs (e.g., `/pokemon/{id}`)

3. **Request Matching** (`ReplayerInterceptor.intercept()`)
   ```kotlin
   // When app makes a request in REPLAY mode
   val request = chain.request()
   val bodySha256 = safeBodySha256(request.body)
   
   // Try to find a matching recorded response
   val recordedResponse = tape?.find(request, bodySha256)
   ```

4. **Pattern Matching** (`UrlPattern.matches()`)
    - URLs with dynamic segments are matched using patterns:
   ```
   Pattern: https://pokeapi.co/api/v2/pokemon/{id}
   Request: https://pokeapi.co/api/v2/pokemon/25
   Match: ✓ (extracts id=25)
   ```

5. **Response Replay**
   ```kotlin
   if (recordedResponse != null) {
       // Build a fake OkHttp Response from recorded data
       return Response.Builder()
           .request(request)
           .protocol(Protocol.HTTP_1_1)
           .code(recordedResponse.code)
           .message("OK")
           .headers(Headers.headersOf(*headerPairs))
           .body(recordedResponse.body.toResponseBody(mediaType))
           .build()
   } else {
       // No match found - throw exception or fall back
       throw NoTapeFoundException("No recorded response found for request")
   }
   ```

6. **No Network Call**
    - The response is served entirely from the tape file
    - No actual HTTP request is made
    - The app behaves exactly as it did during recording

#### **Phase 4: Runtime Mode Switching**

The app supports switching modes at runtime:

```kotlin
// Switch from RECORD to REPLAY
app.switchMode(Mode.REPLAY)

// This rebuilds the HTTP client with the appropriate interceptor
okHttp = client(
    context = this,
    config = AppConfig(mode = Mode.REPLAY, tapeFile = config.tapeFile)
)
```

---

### The Three Modes

| Mode | Behavior | Use Case |
|------|----------|----------|
| **RECORD** | Makes real network calls and records them to tape | Initial recording, updating test data |
| **REPLAY** | Serves responses from tape, no network calls | Testing, offline development, CI/CD |
| **PASSTHROUGH** | Plain HTTP client, no recording or replay | Production, when you don't need VCR |

---

### Event Types

The system uses four event types (all stored in NDJSON format):

#### 1. **SESSION_START**
Marks the beginning of a recording session:
```json
{
  "type": "SESSION_START",
  "ts": 1733420800000,
  "appVersion": "1.0",
  "device": "Pixel 6"
}
```

#### 2. **ACTION**
User interactions or app events:
```json
{
  "type": "ACTION",
  "ts": 1733420801000,
  "name": "BUTTON_CLICK",
  "details": {"button": "fetch_pokemon"}
}
```

#### 3. **REQUEST**
HTTP request metadata:
```json
{
  "type": "REQUEST",
  "ts": 1733420801100,
  "requestId": "abc-123",
  "method": "GET",
  "url": "https://pokeapi.co/api/v2/pokemon/25",
  "bodySha256": null
}
```

#### 4. **RESPONSE**
HTTP response with full body:
```json
{
  "type": "RESPONSE",
  "ts": 1733420801350,
  "requestId": "abc-123",
  "code": 200,
  "headers": {"content-type": "application/json"},
  "body": "{\"name\":\"pikachu\",\"id\":25}",
  "durationMs": 250
}
```

---

### File Structure

```
app/files/sessions/
└── events.ndjson          # Single append-only tape file
```

The tape file grows over time as you record more sessions. Each `SESSION_START` event marks a new session boundary. When replaying, you can choose to load:
- The entire file (all sessions)
- Just the latest session
- A specific session by timestamp

---

### Complete Workflow Example

#### **Recording a Session**

1. Launch app in RECORD mode
2. App writes `SESSION_START` event
3. User taps "Fetch Pokemon" button
4. App logs `ACTION` event
5. App makes HTTP GET to `https://pokeapi.co/api/v2/pokemon/25`
6. `RecordingInterceptor` logs `REQUEST` event
7. Network call completes
8. `RecordingInterceptor` logs `RESPONSE` event with full JSON body
9. App displays Pikachu data
10. Session data saved to `events.ndjson`

#### **Replaying the Session**

1. Launch app in REPLAY mode
2. `TapeLoader` reads `events.ndjson`
3. Parses all events and builds request/response map
4. User taps "Fetch Pokemon" button (same action)
5. App makes HTTP GET to `https://pokeapi.co/api/v2/pokemon/25`
6. `ReplayerInterceptor` intercepts the request
7. Matches URL against pattern `https://pokeapi.co/api/v2/pokemon/{id}`
8. Finds recorded response in tape
9. Returns recorded response immediately (no network call)
10. App displays Pikachu data (identical to recording)

---

### Key Design Decisions

#### **Why NDJSON?**
- Human-readable for debugging
- Append-only (no need to rewrite entire file)
- Easy to parse line-by-line
- Each line is valid JSON (can be inspected individually)

#### **Why Pattern Matching?**
- APIs often have dynamic URLs (`/users/123`, `/users/456`)
- Pattern matching allows one recorded response to match multiple similar requests
- Retrofit-style patterns (`/users/{id}`) are intuitive for developers

#### **Why Single-Threaded Executor for Recording?**
- Ensures events are written in chronological order
- Prevents race conditions when multiple threads log events
- Async writes don't block the main thread or network calls

---

### Usage in Tests

```kotlin
@Test
fun testPokemonFetch() {
    // Set up app in REPLAY mode with a specific tape
    val app = VcrApp.from(context)
    app.switchMode(Mode.REPLAY)
    
    // Make request - will be served from tape
    val response = app.okHttp.newCall(
        Request.Builder()
            .url("https://pokeapi.co/api/v2/pokemon/25")
            .build()
    ).execute()
    
    // Assert on recorded response
    assertEquals(200, response.code)
    assertTrue(response.body?.string()?.contains("pikachu") == true)
}
```

---

### Summary

**VCR** implements the VCR pattern to make Android app testing and development more reliable and efficient. By recording real HTTP interactions and replaying them later, you get:

- **Deterministic tests** that don't depend on external APIs
- **Faster test execution** (no network latency)
- **Offline development** capability
- **Bug reproduction** from recorded sessions
- **Pattern-based matching** for dynamic URLs

The architecture organizes the recording/replay logic into well-structured packages within a single Android application module, making it maintainable and easy to integrate into existing projects.