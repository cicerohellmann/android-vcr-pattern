package com.hellmannratti.vcr.sessionkit

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * AC-0.4: Characterization tests for tape load diagnostics (malformed files, empty tapes)
 *
 * These tests capture the current behavior when loading tapes with various issues:
 * - Loading malformed tape files (invalid JSON/NDJSON, corrupted data)
 * - Loading empty tape files
 * - Proper diagnostic messages/errors for each case
 *
 * The goal is to freeze behavior around tape load diagnostics before refactoring
 * the tape loading system.
 */
class TapeLoadDiagnosticsCharacterizationTest {

    // ============================================================================
    // Tests for malformed JSON/NDJSON files
    // ============================================================================

    @Test
    fun `malformed JSON - invalid JSON object throws IllegalArgumentException with error info`() {
        val dir = createTempDir(prefix = "sessionkit_malformed_")
        try {
            val file = File(dir, "bad.ndjson")
            file.writeText("{not json}")

            val error = runCatching {
                TapeLoader.loadLatestSession(file)
            }.exceptionOrNull()

            requireNotNull(error)
            assertTrue(error is IllegalArgumentException)
            assertTrue(error.message.orEmpty().contains("Malformed event"))
            assertTrue(error.message.orEmpty().contains("line 1"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `malformed JSON - missing closing brace throws with location info`() {
        val dir = createTempDir(prefix = "sessionkit_malformed_")
        try {
            val file = File(dir, "incomplete.ndjson")
            file.writeText("""{"type":"SESSION_START","appVersion":"1.0""")

            val error = runCatching {
                TapeLoader.loadLatestSession(file)
            }.exceptionOrNull()

            requireNotNull(error)
            assertTrue(error is IllegalArgumentException)
            assertTrue(error.message.orEmpty().contains("Malformed event"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `malformed JSON - invalid event type throws with descriptive error`() {
        val dir = createTempDir(prefix = "sessionkit_malformed_")
        try {
            val file = File(dir, "bad_type.ndjson")
            file.writeText(
                buildString {
                    appendLine(
                        "{\"schema\":1,\"seq\":0,\"type\":\"UNKNOWN_EVENT\",\"ts\":1700000000000,\"metadata\":{}}"
                    )
                }
            )

            val error = runCatching {
                TapeLoader.loadLatestSession(file)
            }.exceptionOrNull()

            requireNotNull(error)
            assertTrue(error is IllegalArgumentException)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `malformed JSON - truncated line in NDJSON preserves context in error`() {
        val dir = createTempDir(prefix = "sessionkit_malformed_")
        try {
            val file = File(dir, "truncated.ndjson")
            file.writeText(
                buildString {
                    appendLine(
                        "{\"schema\":1,\"seq\":0,\"type\":\"SESSION_START\",\"ts\":1700000000000,\"metadata\":{},\"appVersion\":\"1.0\",\"device\":\"test\"}"
                    )
                    appendLine("{\"schema\":1,\"seq\":1,\"type\":\"REQUEST\",\"requestId\":\"r1\",\"method\":\"GET\"")
                }
            )

            val error = runCatching {
                TapeLoader.loadLatestSession(file)
            }.exceptionOrNull()

            requireNotNull(error)
            assertTrue(error is IllegalArgumentException)
            assertTrue(error.message.orEmpty().contains("line 2"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `malformed JSON - invalid type in enum throws during deserialization`() {
        val dir = createTempDir(prefix = "sessionkit_malformed_")
        try {
            val file = File(dir, "bad_enum.ndjson")
            file.writeText(
                buildString {
                    appendLine(
                        "{\"schema\":1,\"seq\":0,\"type\":\"SESSION_START\",\"ts\":1700000000000,\"metadata\":{},\"appVersion\":\"1.0\",\"device\":\"test\"}"
                    )
                    appendLine(
                        "{\"schema\":1,\"seq\":1,\"type\":\"REQUEST\",\"ts\":1700000000100,\"metadata\":{},\"requestId\":\"r1\",\"method\":\"INVALID_METHOD\",\"url\":\"https://example.com/items\",\"bodySha256\":null}"
                    )
                }
            )

            val error = runCatching {
                TapeLoader.loadLatestSession(file)
            }.exceptionOrNull()

            requireNotNull(error)
            assertTrue(error is IllegalArgumentException)
            assertTrue(error.message.orEmpty().contains("Malformed event"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `malformed JSON - response before request references unknown requestId with error`() {
        val dir = createTempDir(prefix = "sessionkit_malformed_")
        try {
            val file = File(dir, "bad_order.ndjson")
            file.writeText(
                buildString {
                    appendLine(
                        "{\"schema\":1,\"seq\":0,\"type\":\"SESSION_START\",\"ts\":1700000000000,\"metadata\":{},\"appVersion\":\"1.0\",\"device\":\"test\"}"
                    )
                    appendLine(
                        "{\"schema\":1,\"seq\":1,\"type\":\"RESPONSE\",\"ts\":1700000000100,\"metadata\":{},\"requestId\":\"unknown_r1\",\"code\":200,\"headers\":{},\"body\":\"early\",\"durationMs\":1}"
                    )
                }
            )

            val error = runCatching {
                TapeLoader.loadLatestSession(file)
            }.exceptionOrNull()

            requireNotNull(error)
            assertTrue(error is IllegalArgumentException)
            assertTrue(error.message.orEmpty().contains("unknown requestId"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `malformed JSON - unsupported schema version throws with version info`() {
        val dir = createTempDir(prefix = "sessionkit_malformed_")
        try {
            val file = File(dir, "bad_schema.ndjson")
            file.writeText(
                buildString {
                    appendLine(
                        "{\"schema\":99,\"seq\":0,\"type\":\"SESSION_START\",\"ts\":1700000000000,\"metadata\":{},\"appVersion\":\"1.0\",\"device\":\"test\"}"
                    )
                }
            )

            val error = runCatching {
                TapeLoader.loadLatestSession(file)
            }.exceptionOrNull()

            requireNotNull(error)
            assertTrue(error is IllegalArgumentException)
            assertTrue(error.message.orEmpty().contains("Unsupported schema"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `malformed JSON - negative seq value throws with validation error`() {
        val dir = createTempDir(prefix = "sessionkit_malformed_")
        try {
            val file = File(dir, "bad_seq.ndjson")
            file.writeText(
                buildString {
                    appendLine(
                        "{\"schema\":1,\"seq\":-1,\"type\":\"SESSION_START\",\"ts\":1700000000000,\"metadata\":{},\"appVersion\":\"1.0\",\"device\":\"test\"}"
                    )
                }
            )

            val error = runCatching {
                TapeLoader.loadLatestSession(file)
            }.exceptionOrNull()

            requireNotNull(error)
            assertTrue(error is IllegalArgumentException)
            assertTrue(error.message.orEmpty().contains("Invalid seq"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `malformed JSON - very long line truncates in error message for readability`() {
        val dir = createTempDir(prefix = "sessionkit_malformed_")
        try {
            val file = File(dir, "long_line.ndjson")
            val veryLongString = "x".repeat(500)
            file.writeText("""{not json "$veryLongString""}""")

            val error = runCatching {
                TapeLoader.loadLatestSession(file)
            }.exceptionOrNull()

            requireNotNull(error)
            val message = error.message.orEmpty()
            assertTrue(message.contains("Malformed event"))
            // Error message should truncate long lines to ~200 chars
            assertTrue(message.length < 300 || message.contains("..."))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `malformed JSON - corrupted character encoding shows in error preview`() {
        val dir = createTempDir(prefix = "sessionkit_malformed_")
        try {
            val file = File(dir, "corrupted.ndjson")
            file.writeBytes(
                byteArrayOf(
                    0x7B.toByte(), // {
                    0x22.toByte(), // "
                    0x74.toByte(), // t
                    0x79.toByte(), // y
                    0x70.toByte(), // p
                    0x65.toByte(), // e
                    0x22.toByte(), // "
                    0x3A.toByte(), // :
                    0xFF.toByte(), // invalid UTF-8
                    0xFE.toByte()  // invalid UTF-8
                )
            )

            val error = runCatching {
                TapeLoader.loadLatestSession(file)
            }.exceptionOrNull()

            requireNotNull(error)
            assertTrue(error is IllegalArgumentException)
            assertTrue(error.message.orEmpty().contains("Malformed event"))
        } finally {
            dir.deleteRecursively()
        }
    }

    // ============================================================================
    // Tests for empty tapes and tape files with no responses
    // ============================================================================

    @Test
    fun `empty tape file - truly empty file throws NoTapeFoundException`() {
        val dir = createTempDir(prefix = "sessionkit_empty_")
        try {
            val file = File(dir, "empty.ndjson")
            file.writeText("")

            val error = runCatching {
                TapeLoader.loadLatestSession(file)
            }.exceptionOrNull()

            requireNotNull(error)
            assertTrue(error is NoTapeFoundException)
            assertTrue(error.message.orEmpty().contains("contains no recorded responses"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `empty tape file - only whitespace and blank lines throws NoTapeFoundException`() {
        val dir = createTempDir(prefix = "sessionkit_empty_")
        try {
            val file = File(dir, "blank.ndjson")
            file.writeText("   \n\n   \n\n")

            val error = runCatching {
                TapeLoader.loadLatestSession(file)
            }.exceptionOrNull()

            requireNotNull(error)
            assertTrue(error is NoTapeFoundException)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `empty tape file - session start but no requests throws NoTapeFoundException`() {
        val dir = createTempDir(prefix = "sessionkit_empty_")
        try {
            val file = File(dir, "no_requests.ndjson")
            file.writeText(
                buildString {
                    appendLine(
                        "{\"schema\":1,\"seq\":0,\"type\":\"SESSION_START\",\"ts\":1700000000000,\"metadata\":{},\"appVersion\":\"1.0\",\"device\":\"test\"}"
                    )
                }
            )

            val error = runCatching {
                TapeLoader.loadLatestSession(file)
            }.exceptionOrNull()

            requireNotNull(error)
            assertTrue(error is NoTapeFoundException)
            assertTrue(error.message.orEmpty().contains("contains no recorded responses"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `empty tape file - requests but no responses throws NoTapeFoundException`() {
        val dir = createTempDir(prefix = "sessionkit_empty_")
        try {
            val file = File(dir, "no_responses.ndjson")
            file.writeText(
                buildString {
                    appendLine(
                        "{\"schema\":1,\"seq\":0,\"type\":\"SESSION_START\",\"ts\":1700000000000,\"metadata\":{},\"appVersion\":\"1.0\",\"device\":\"test\"}"
                    )
                    appendLine(
                        "{\"schema\":1,\"seq\":1,\"type\":\"REQUEST\",\"ts\":1700000000100,\"metadata\":{},\"requestId\":\"r1\",\"method\":\"GET\",\"url\":\"https://example.com/items\",\"bodySha256\":null}"
                    )
                }
            )

            val error = runCatching {
                TapeLoader.loadLatestSession(file)
            }.exceptionOrNull()

            requireNotNull(error)
            assertTrue(error is NoTapeFoundException)
            assertTrue(error.message.orEmpty().contains("contains no recorded responses"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `empty tape file - UI events but no request/response pairs throws NoTapeFoundException`() {
        val dir = createTempDir(prefix = "sessionkit_empty_")
        try {
            val file = File(dir, "ui_only.ndjson")
            file.writeText(
                buildString {
                    appendLine(
                        "{\"schema\":1,\"seq\":0,\"type\":\"SESSION_START\",\"ts\":1700000000000,\"metadata\":{},\"appVersion\":\"1.0\",\"device\":\"test\"}"
                    )
                    appendLine(
                        "{\"schema\":1,\"seq\":1,\"type\":\"UI_EVENT\",\"ts\":1700000000100,\"metadata\":{},\"screen\":\"ApiTest\",\"event\":\"Open\"}"
                    )
                    appendLine(
                        "{\"schema\":1,\"seq\":2,\"type\":\"UI_EVENT\",\"ts\":1700000000200,\"metadata\":{},\"screen\":\"ApiTest\",\"event\":\"ButtonClick\"}"
                    )
                }
            )

            val error = runCatching {
                TapeLoader.loadLatestSession(file)
            }.exceptionOrNull()

            requireNotNull(error)
            assertTrue(error is NoTapeFoundException)
            assertTrue(error.message.orEmpty().contains("contains no recorded responses"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `empty tape file - actions but no request/response pairs throws NoTapeFoundException`() {
        val dir = createTempDir(prefix = "sessionkit_empty_")
        try {
            val file = File(dir, "actions_only.ndjson")
            file.writeText(
                buildString {
                    appendLine(
                        "{\"schema\":1,\"seq\":0,\"type\":\"SESSION_START\",\"ts\":1700000000000,\"metadata\":{},\"appVersion\":\"1.0\",\"device\":\"test\"}"
                    )
                    appendLine(
                        "{\"schema\":1,\"seq\":1,\"type\":\"ACTION\",\"ts\":1700000000100,\"metadata\":{},\"name\":\"login\",\"details\":{}}"
                    )
                }
            )

            val error = runCatching {
                TapeLoader.loadLatestSession(file)
            }.exceptionOrNull()

            requireNotNull(error)
            assertTrue(error is NoTapeFoundException)
            assertTrue(error.message.orEmpty().contains("contains no recorded responses"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `empty tape file - multiple sessions, last one empty throws NoTapeFoundException`() {
        val dir = createTempDir(prefix = "sessionkit_empty_")
        try {
            val file = File(dir, "last_empty.ndjson")
            file.writeText(
                buildString {
                    // First session with a complete request/response
                    appendLine(
                        "{\"schema\":1,\"seq\":0,\"type\":\"SESSION_START\",\"ts\":1,\"metadata\":{},\"appVersion\":\"1.0\",\"device\":\"emulator\"}"
                    )
                    appendLine(
                        "{\"schema\":1,\"seq\":1,\"type\":\"REQUEST\",\"ts\":2,\"metadata\":{},\"requestId\":\"r1\",\"method\":\"GET\",\"url\":\"https://x/a\",\"bodySha256\":null}"
                    )
                    appendLine(
                        "{\"schema\":1,\"seq\":2,\"type\":\"RESPONSE\",\"ts\":3,\"metadata\":{},\"requestId\":\"r1\",\"code\":200,\"headers\":{},\"body\":\"a\",\"durationMs\":0}"
                    )
                    // Second session (latest) with no responses
                    appendLine(
                        "{\"schema\":1,\"seq\":3,\"type\":\"SESSION_START\",\"ts\":4,\"metadata\":{},\"appVersion\":\"1.0\",\"device\":\"emulator\"}"
                    )
                    appendLine(
                        "{\"schema\":1,\"seq\":4,\"type\":\"REQUEST\",\"ts\":5,\"metadata\":{},\"requestId\":\"r2\",\"method\":\"GET\",\"url\":\"https://x/b\",\"bodySha256\":null}"
                    )
                }
            )

            val error = runCatching {
                TapeLoader.loadLatestSession(file)
            }.exceptionOrNull()

            requireNotNull(error)
            assertTrue(error is NoTapeFoundException)
            assertTrue(error.message.orEmpty().contains("contains no recorded responses"))
        } finally {
            dir.deleteRecursively()
        }
    }

    // ============================================================================
    // Tests for file not found
    // ============================================================================

    @Test
    fun `file not found - missing tape file throws NoTapeFoundException`() {
        val dir = createTempDir(prefix = "sessionkit_missing_")
        try {
            val file = File(dir, "missing.ndjson")

            val error = runCatching {
                TapeLoader.loadLatestSession(file)
            }.exceptionOrNull()

            requireNotNull(error)
            assertTrue(error is NoTapeFoundException)
            assertTrue(error.message.orEmpty().contains("No tape file found"))
            assertTrue(error.message.orEmpty().contains("missing.ndjson"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `file not found - nonexistent directory path throws NoTapeFoundException`() {
        val file = File("/nonexistent/path/tape.ndjson")

        val error = runCatching {
            TapeLoader.loadLatestSession(file)
        }.exceptionOrNull()

        requireNotNull(error)
        assertTrue(error is NoTapeFoundException)
        assertTrue(error.message.orEmpty().contains("No tape file found"))
    }

    // ============================================================================
    // Tests for diagnostic message quality
    // ============================================================================

    @Test
    fun `diagnostics - error message includes file name for missing tape`() {
        val dir = createTempDir(prefix = "sessionkit_missing_")
        try {
            val file = File(dir, "specific_tape_name.ndjson")

            val error = runCatching {
                TapeLoader.loadLatestSession(file)
            }.exceptionOrNull()

            requireNotNull(error)
            assertTrue(error.message.orEmpty().contains("specific_tape_name"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `diagnostics - error message includes line number for malformed JSON`() {
        val dir = createTempDir(prefix = "sessionkit_malformed_")
        try {
            val file = File(dir, "bad.ndjson")
            file.writeText(
                buildString {
                    appendLine(
                        "{\"schema\":1,\"seq\":0,\"type\":\"SESSION_START\",\"ts\":1700000000000,\"metadata\":{},\"appVersion\":\"1.0\",\"device\":\"test\"}"
                    )
                    appendLine("{bad json on line 2")
                    appendLine(
                        "{\"schema\":1,\"seq\":2,\"type\":\"REQUEST\",\"ts\":1700000000200,\"metadata\":{},\"requestId\":\"r1\",\"method\":\"GET\",\"url\":\"https://example.com/items\",\"bodySha256\":null}"
                    )
                }
            )

            val error = runCatching {
                TapeLoader.loadLatestSession(file)
            }.exceptionOrNull()

            requireNotNull(error)
            val message = error.message.orEmpty()
            assertTrue(message.contains("line 2"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `diagnostics - error includes tape file path for diagnostic logging`() {
        val dir = createTempDir(prefix = "sessionkit_missing_")
        try {
            val file = File(dir, "missing.ndjson")

            val error = runCatching {
                TapeLoader.loadLatestSession(file)
            }.exceptionOrNull()

            requireNotNull(error)
            assertTrue(error.message.orEmpty().contains(file.absolutePath))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `diagnostics - error message for empty tape includes file path`() {
        val dir = createTempDir(prefix = "sessionkit_empty_")
        try {
            val file = File(dir, "empty_tape.ndjson")
            file.writeText("")

            val error = runCatching {
                TapeLoader.loadLatestSession(file)
            }.exceptionOrNull()

            requireNotNull(error)
            assertTrue(error.message.orEmpty().contains("empty_tape.ndjson"))
        } finally {
            dir.deleteRecursively()
        }
    }
}
