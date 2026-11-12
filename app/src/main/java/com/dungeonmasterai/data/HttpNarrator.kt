package com.dungeonmasterai.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HttpNarrator(
    private val baseUrl: String,
    private val connectTimeoutMs: Int = TIMEOUT_MS,
    private val readTimeoutMs: Int = TIMEOUT_MS,
) : Narrator {
    init {
        require(baseUrl.isNotBlank()) { "Narrator URL must not be blank." }
    }

    override suspend fun complete(request: NarratorRequest): String = withContext(Dispatchers.IO) {
        val connection = (URI(baseUrl).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val body = buildString {
                append("{\"sceneId\":")
                append(jsonString(request.sceneId))
                append(",\"actionText\":")
                append(jsonString(request.actionText))
                append(",\"characterName\":")
                append(jsonString(request.characterName))
                append(",\"outcome\":")
                append(jsonString(request.outcomeName))
                append('}')
            }.toByteArray(Charsets.UTF_8)
            connection.outputStream.use { it.write(body) }
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException("Narrator responded $code.")
            }
            val text = connection.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
            if (text.isBlank()) {
                throw IOException("Narrator response was empty.")
            }
            text
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS: Int = 10_000
    }
}
