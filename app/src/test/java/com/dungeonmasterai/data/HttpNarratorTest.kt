package com.dungeonmasterai.data

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpNarratorTest {
    @Test
    fun postsTheActionAndReturnsTheBodyWithoutAKey() = runBlocking {
        var authorization: String? = "unset"
        var body = ""
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            authorization = exchange.requestHeaders.getFirst("Authorization")
            body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            val response = """{"narration":"The spear dips.","intent":"ATTACK"}"""
            val bytes = response.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val narrator = HttpNarrator("http://127.0.0.1:${server.address.port}/")
            val raw = narrator.complete(
                NarratorRequest(
                    sceneId = "guardians_bridge",
                    actionText = "say \"pass\"",
                    characterName = "Ada",
                    outcomeName = "ONGOING",
                ),
            )

            assertNull(authorization)
            assertTrue(body.contains("say \\\"pass\\\""))
            assertTrue(body.contains("guardians_bridge"))
            assertEquals("""{"narration":"The spear dips.","intent":"ATTACK"}""", raw)
        } finally {
            server.stop(0)
        }
    }
}
