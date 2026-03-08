package com.hellmannratti.cassete.core

import java.util.UUID
import kotlin.random.Random

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

class FixedClock(
    private var nowMsValue: Long,
    private var nowNsValue: Long = nowMsValue * 1_000_000
) : Clock {
    override fun nowMs(): Long = nowMsValue
    override fun nowNs(): Long = nowNsValue

    fun advanceMs(deltaMs: Long) {
        nowMsValue += deltaMs
        nowNsValue += deltaMs * 1_000_000
    }
}

class SequenceIdGenerator(
    private val ids: ArrayDeque<String>
) : IdGenerator {
    override fun uuid(): String = ids.removeFirstOrNull() ?: error("SequenceIdGenerator exhausted")
}

class SequenceRandomProvider(
    private val values: ArrayDeque<Int>
) : RandomProvider {
    override fun nextInt(from: Int, until: Int): Int {
        val value = values.removeFirstOrNull() ?: error("SequenceRandomProvider exhausted")
        require(value in from until until) { "Value $value not in range [$from, $until)" }
        return value
    }
}

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
