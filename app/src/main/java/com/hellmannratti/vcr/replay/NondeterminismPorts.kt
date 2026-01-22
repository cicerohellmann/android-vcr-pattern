package com.hellmannratti.vcr.replay

import java.util.UUID
import kotlin.random.Random

/**
 * Task 03: Abstract Nondeterminism
 *
 * These small interfaces (“ports”) isolate nondeterministic inputs so they can be swapped
 * for deterministic implementations in tests and in REPLAY.
 */
interface Clock {
    fun nowMs(): Long

    /**
     * Monotonic time source (suitable for durations).
     *
     * Note: this is NOT directly comparable to [nowMs].
     */
    fun nowNs(): Long
}

object SystemClock : Clock {
    override fun nowMs(): Long = System.currentTimeMillis()
    override fun nowNs(): Long = System.nanoTime()
}

interface IdGenerator {
    fun uuid(): String
}

object UuidGenerator : IdGenerator {
    override fun uuid(): String = UUID.randomUUID().toString()
}

interface RandomProvider {
    fun nextInt(from: Int, until: Int): Int
}

class KotlinRandomProvider(
    private val random: Random
) : RandomProvider {
    override fun nextInt(from: Int, until: Int): Int = random.nextInt(from, until)
}

/** Simple deterministic clock for unit tests. */
class FixedClock(
    private var nowMs: Long,
    private var nowNs: Long = nowMs * 1_000_000
) : Clock {
    override fun nowMs(): Long = nowMs
    override fun nowNs(): Long = nowNs

    fun advanceMs(deltaMs: Long) {
        nowMs += deltaMs
        nowNs += deltaMs * 1_000_000
    }
}

/** Simple deterministic id generator for unit tests. */
class SequenceIdGenerator(
    private val ids: ArrayDeque<String>
) : IdGenerator {
    override fun uuid(): String = ids.removeFirstOrNull() ?: error("SequenceIdGenerator exhausted")
}

/** Simple deterministic random provider for unit tests. */
class SequenceRandomProvider(
    private val values: ArrayDeque<Int>
) : RandomProvider {
    override fun nextInt(from: Int, until: Int): Int {
        val v = values.removeFirstOrNull() ?: error("SequenceRandomProvider exhausted")
        require(v in from until until) { "Value $v not in range [$from, $until)" }
        return v
    }
}
