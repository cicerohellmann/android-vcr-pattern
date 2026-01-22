package com.hellmannratti.vcr.sessionkit

import java.util.UUID
import kotlin.random.Random

/** Task 03: Abstract nondeterminism. */
interface Clock {
    fun nowMs(): Long
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

/** Minimal network abstraction used by screen logic. */
interface NetworkClient {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): NetworkResponse
    suspend fun post(
        url: String,
        body: String,
        contentType: String = "application/json",
        headers: Map<String, String> = emptyMap()
    ): NetworkResponse
}

data class NetworkResponse(
    val code: Int,
    val headers: Map<String, String>,
    val body: String
) {
    fun requireSuccess(): NetworkResponse {
        if (code !in 200..299) error("HTTP $code")
        return this
    }
}
