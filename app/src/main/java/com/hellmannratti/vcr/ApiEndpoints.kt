package com.hellmannratti.vcr

import com.hellmannratti.cassete.core.UrlPattern

data class ApiEndpoints(
    val pokemonBaseUrl: String = "https://pokeapi.co/api/v2",
    val githubBaseUrl: String = "https://api.github.com",
    val jsonPlaceholderBaseUrl: String = "https://jsonplaceholder.typicode.com"
) {
    fun pokemon(id: Int): String = joinPath(pokemonBaseUrl, "pokemon/$id")

    fun githubUser(username: String): String = joinPath(githubBaseUrl, "users/$username")

    fun jsonPlaceholder(endpoint: String, id: Int): String =
        joinPath(jsonPlaceholderBaseUrl, "$endpoint/$id")

    fun urlPatterns(): List<UrlPattern> {
        return listOf(
            UrlPattern.fromRetrofitStyle(joinPath(pokemonBaseUrl, "pokemon/{id}")),
            UrlPattern.fromRetrofitStyle(joinPath(githubBaseUrl, "users/{name}")),
            UrlPattern.fromRetrofitStyle(joinPath(jsonPlaceholderBaseUrl, "posts/{id}")),
            UrlPattern.fromRetrofitStyle(joinPath(jsonPlaceholderBaseUrl, "users/{id}"))
        )
    }

    private fun joinPath(base: String, suffix: String): String {
        return "${base.trimEnd('/')}/${suffix.trimStart('/')}"
    }
}

object AppTestOverrides {
    @Volatile
    var apiEndpoints: ApiEndpoints? = null

    @Volatile
    var suppressToasts: Boolean = false

    fun reset() {
        apiEndpoints = null
        suppressToasts = false
    }
}
