package com.hellmannratti.cassete.core

fun interface UrlNormalizer {
    fun normalize(url: String): String
}

fun interface HeaderRedactor {
    fun redact(name: String, value: String): String?

    companion object {
        fun keepAll(): HeaderRedactor = HeaderRedactor { _, value -> value }

        fun redactAuthTokens(replacement: String = "<redacted>"): HeaderRedactor =
            HeaderRedactor { name, value ->
                if (name.equals("authorization", ignoreCase = true)) replacement else value
            }
    }
}

fun interface BodyRedactor {
    fun redact(contentType: String?, bodyUtf8: String): String

    companion object {
        fun keepBody(): BodyRedactor = BodyRedactor { _, body -> body }
    }
}

class UrlPattern private constructor(
    private val pattern: String,
    private val segments: List<Segment>
) : UrlNormalizer {

    sealed class Segment {
        data class Literal(val value: String) : Segment()
        data class Placeholder(val name: String) : Segment()
    }

    fun matches(url: String): Boolean {
        val urlSegments = url.split("/")
        if (urlSegments.size != segments.size) return false

        for (i in segments.indices) {
            when (val segment = segments[i]) {
                is Segment.Literal -> if (urlSegments[i] != segment.value) return false
                is Segment.Placeholder -> if (urlSegments[i].isEmpty()) return false
            }
        }

        return true
    }

    override fun normalize(url: String): String {
        if (!matches(url)) return url

        val normalizedSegments = url.split("/").toMutableList()
        val patternSegments = pattern.split("/")
        for (i in segments.indices) {
            if (segments[i] is Segment.Placeholder) {
                normalizedSegments[i] = patternSegments[i]
            }
        }
        return normalizedSegments.joinToString("/")
    }

    override fun toString(): String = pattern

    companion object {
        fun fromRetrofitStyle(pattern: String): UrlPattern {
            val segments = pattern.split("/").map { part ->
                when {
                    part.isEmpty() -> Segment.Literal("")
                    part.startsWith("{") && part.endsWith("}") -> Segment.Placeholder(
                        name = part.substring(1, part.length - 1)
                    )
                    else -> Segment.Literal(part)
                }
            }
            return UrlPattern(pattern = pattern, segments = segments)
        }
    }
}
