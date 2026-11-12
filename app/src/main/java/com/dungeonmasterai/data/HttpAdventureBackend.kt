package com.dungeonmasterai.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException

class HttpAdventureBackend(
    baseUrl: String,
    allowCleartext: Boolean,
    private val connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
    private val readTimeoutMs: Int = READ_TIMEOUT_MS,
) : AdventureBackend {
    private val origin: String

    init {
        requireAdventureBackendUrl(baseUrl, allowCleartext)
        origin = baseUrl.trimEnd('/')
    }

    override suspend fun interpret(request: InterpretationRequest): BackendCall<InterpretationResponse> =
        post("/v1/interpret", backendJson.encodeToString(InterpretationRequest.serializer(), request)) { text ->
            backendJson.decodeFromString(InterpretationResponse.serializer(), text)
        }

    override suspend fun narrate(request: NarrationRequest): BackendCall<NarrationResponse> =
        post("/v1/narrate", backendJson.encodeToString(NarrationRequest.serializer(), request)) { text ->
            backendJson.decodeFromString(NarrationResponse.serializer(), text)
        }

    override suspend fun scene(request: SceneRequest): BackendCall<SceneResponse> =
        post("/v1/scenes", backendJson.encodeToString(SceneRequest.serializer(), request)) { text ->
            backendJson.decodeFromString(SceneResponse.serializer(), text)
        }

    private suspend fun <T> post(path: String, body: String, decode: (String) -> T): BackendCall<T> =
        withContext(Dispatchers.IO) {
            val connection = (URI(origin + path).toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }
            try {
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.use { it.readBytes() }?.toString(Charsets.UTF_8).orEmpty()
                if (code !in 200..299) {
                    return@withContext failed(text)
                }
                try {
                    BackendCall.Ready(decode(text))
                } catch (error: SerializationException) {
                    BackendCall.Failed(code = "INVALID_MODEL_OUTPUT", retryable = false)
                }
            } catch (error: SocketTimeoutException) {
                BackendCall.Failed(code = "PROVIDER_TIMEOUT", retryable = true)
            } catch (error: IOException) {
                BackendCall.Failed(code = "PROVIDER_ERROR", retryable = true)
            } finally {
                connection.disconnect()
            }
        }

    private fun failed(text: String): BackendCall<Nothing> {
        val body = try {
            backendJson.decodeFromString(ErrorEnvelope.serializer(), text).error
        } catch (error: SerializationException) {
            return BackendCall.Failed(code = "PROVIDER_ERROR", retryable = false)
        } catch (error: IllegalArgumentException) {
            return BackendCall.Failed(code = "PROVIDER_ERROR", retryable = false)
        }
        return BackendCall.Failed(code = body.code, retryable = body.retryable)
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS: Int = 15_000
        const val READ_TIMEOUT_MS: Int = 60_000
    }
}
