package com.hellmannratti.vcr.sessionkit

/**
 * Pattern matcher for URLs with dynamic segments (e.g., "/users/{id}").
 * Used to normalize URLs during replay so that requests with different parameter values
 * can match the same recorded response pattern.
 */
class UrlPattern private constructor(
    private val pattern: String,
    private val segments: List<Segment>
) {

    sealed class Segment {
        data class Literal(val value: String) : Segment()
        data class Placeholder(val name: String) : Segment()
    }

    /**
     * Check if the given URL matches this pattern.
     * Returns true if the URL structure matches (same number of segments, literals match).
     */
    fun matches(url: String): Boolean {
        val urlSegments = url.split("/")
        val patternSegments = pattern.split("/")

        if (urlSegments.size != patternSegments.size) return false

        for (i in segments.indices) {
            when (val seg = segments[i]) {
                is Segment.Literal -> {
                    if (i >= urlSegments.size || urlSegments[i] != seg.value) {
                        return false
                    }
                }

                is Segment.Placeholder -> {
                    // Placeholder matches any non-empty segment
                    if (i >= urlSegments.size || urlSegments[i].isEmpty()) {
                        return false
                    }
                }
            }
        }

        return true
    }

    /**
     * Normalize a URL by replacing dynamic segments with placeholders.
     * Example: normalize("https://pokeapi.co/api/v2/pokemon/25")
     *       -> "https://pokeapi.co/api/v2/pokemon/{id}"
     */
    fun normalize(url: String): String {
        val urlSegments = url.split("/").toMutableList()
        val patternSegments = pattern.split("/")

        if (urlSegments.size != patternSegments.size) return url

        for (i in segments.indices) {
            if (segments[i] is Segment.Placeholder && i < urlSegments.size) {
                urlSegments[i] = patternSegments[i]
            }
        }

        return urlSegments.joinToString("/")
    }

    override fun toString(): String = pattern

    companion object {
        /**
         * Create a UrlPattern from a Retrofit-style URL with {placeholder} syntax.
         * Example: fromRetrofitStyle("https://pokeapi.co/api/v2/pokemon/{id}")
         */
        fun fromRetrofitStyle(pattern: String): UrlPattern {
            val segments = mutableListOf<Segment>()
            val parts = pattern.split("/")

            for (part in parts) {
                when {
                    part.isEmpty() -> segments.add(Segment.Literal(""))
                    part.startsWith("{") && part.endsWith("}") -> {
                        val name = part.substring(1, part.length - 1)
                        segments.add(Segment.Placeholder(name))
                    }

                    else -> segments.add(Segment.Literal(part))
                }
            }

            return UrlPattern(pattern, segments)
        }
    }
}
