package com.hellmannratti.vcr

import com.hellmannratti.cassete.core.ActionEvent
import com.hellmannratti.cassete.core.TapeLoader
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.io.File
import kotlin.collections.ArrayDeque

/**
 * Replays nondeterministic values from recorded ACTION events while preserving
 * their relative order inside the latest session slice.
 */
class ReplayNondeterminism {
    private sealed interface RecordedInput {
        val seq: Long

        data class RandomInt(
            override val seq: Long,
            val from: Int,
            val until: Int,
            val value: Int
        ) : RecordedInput

        data class TimeMs(
            override val seq: Long,
            val value: Long
        ) : RecordedInput

        data class UuidValue(
            override val seq: Long,
            val value: String
        ) : RecordedInput
    }

    private var sourceFile: String? = null
    private var seed: List<RecordedInput> = emptyList()
    private var queue: ArrayDeque<RecordedInput> = ArrayDeque()

    fun load(file: File) {
        val events = TapeLoader.latestSessionSlice(TapeLoader.parseAndValidateEvents(file.readLines()))
        seed = events
            .filterIsInstance<ActionEvent>()
            .mapNotNull(::toRecordedInput)
        sourceFile = file.absolutePath
        reset()
    }

    fun clear() {
        sourceFile = null
        seed = emptyList()
        queue.clear()
    }

    fun reset() {
        queue = ArrayDeque(seed)
    }

    fun sourceFile(): String? = sourceFile

    fun nextRandomInt(from: Int, until: Int): Int {
        val action = queue.removeFirstOrNull()
            ?: error("Replay is missing recorded ND_RANDOM_INT for range [$from, $until)")
        val recorded = action as? RecordedInput.RandomInt
            ?: error("Replay nondeterminism mismatch: expected ND_RANDOM_INT, got ${action.describe()}")
        require(recorded.from == from && recorded.until == until) {
            "Replay ND_RANDOM_INT mismatch at seq=${recorded.seq}: expected [$from, $until), got [${recorded.from}, ${recorded.until})"
        }
        return recorded.value
    }

    fun nextNowMs(): Long {
        val action = queue.removeFirstOrNull()
            ?: error("Replay is missing recorded ND_TIME")
        val recorded = action as? RecordedInput.TimeMs
            ?: error("Replay nondeterminism mismatch: expected ND_TIME, got ${action.describe()}")
        return recorded.value
    }

    fun nextUuid(): String {
        val action = queue.removeFirstOrNull()
            ?: error("Replay is missing recorded ND_UUID")
        val recorded = action as? RecordedInput.UuidValue
            ?: error("Replay nondeterminism mismatch: expected ND_UUID, got ${action.describe()}")
        return recorded.value
    }

    private fun toRecordedInput(event: ActionEvent): RecordedInput? {
        return when (event.name) {
            "ND_RANDOM_INT" -> {
                val from = (event.details["from"] as? JsonPrimitive)?.intOrNull ?: return null
                val until = (event.details["until"] as? JsonPrimitive)?.intOrNull ?: return null
                val value = (event.details["value"] as? JsonPrimitive)?.intOrNull ?: return null
                RecordedInput.RandomInt(seq = event.seq, from = from, until = until, value = value)
            }

            "ND_TIME" -> {
                val nowMs = (event.details["nowMs"] as? JsonPrimitive)?.longOrNull ?: return null
                RecordedInput.TimeMs(seq = event.seq, value = nowMs)
            }

            "ND_UUID" -> {
                val value = (event.details["value"] as? JsonPrimitive)?.content ?: return null
                RecordedInput.UuidValue(seq = event.seq, value = value)
            }

            else -> null
        }
    }

    private fun RecordedInput.describe(): String = when (this) {
        is RecordedInput.RandomInt -> "ND_RANDOM_INT(seq=$seq)"
        is RecordedInput.TimeMs -> "ND_TIME(seq=$seq)"
        is RecordedInput.UuidValue -> "ND_UUID(seq=$seq)"
    }
}
