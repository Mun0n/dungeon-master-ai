package com.dungeonmasterai.data

import com.dungeonmasterai.domain.adventure.model.Effect
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Live HTTP check for [HttpAdventureBackend].
 * It stays skipped unless ADVENTURE_HTTP_IT=1.
 * ADVENTURE_HTTP_IT_MODE=simulated talks to FastAPI with a scripted provider.
 * That is transport, not a call to a language model.
 * ADVENTURE_HTTP_IT_MODE=real requires a running model and is reported separately.
 */
class AdventureHttpTransportTest {
    @Test
    fun androidClientReachesALiveBackend() = runBlocking {
        assumeTrue(System.getenv("ADVENTURE_HTTP_IT") == "1")
        val base = System.getenv("ADVENTURE_BACKEND_URL") ?: "http://127.0.0.1:8000"
        val mode = System.getenv("ADVENTURE_HTTP_IT_MODE") ?: "simulated"
        val health = health(base)
        val backend = HttpAdventureBackend(
            baseUrl = base,
            allowCleartext = true,
            connectTimeoutMs = 3_000,
            readTimeoutMs = 20_000,
        )
        val contract = SceneContractBody(
            id = "plaza_arrival",
            locationId = "plaza",
            purpose = "Arrive and ask one question.",
            allowedActionIds = listOf("ask_mara", "read_notice"),
            presentNpcIds = listOf("mara"),
            revealableInformation = listOf("The bells ring at midnight."),
            forbiddenInformation = listOf("Aldren keeps the crypt key"),
        )
        val context = AuthorizedContextBody(
            locationName = "Plaza",
            presentNpcs = listOf(NpcBody(id = "mara", name = "Mara", state = "wary")),
            availableActions = listOf(
                ActionBody(id = "ask_mara", label = "Ask Mara"),
                ActionBody(id = "read_notice", label = "Read the notice"),
            ),
            unavailableActions = listOf("open_crypt"),
            requirementBlockedActions = listOf("accuse_local"),
            recentInteractions = emptyList(),
            narrativeSummary = "",
        )
        val interpretation = backend.interpret(
            InterpretationRequest(
                contractVersion = 1,
                requestId = "req-1",
                stateRevision = "rev-1",
                sceneContract = contract,
                authorizedContext = context,
                playerText = "I ask Mara about the bells",
            ),
        )
        val narration = backend.narrate(
            NarrationRequest(
                contractVersion = 1,
                requestId = "req-1",
                stateRevision = "rev-1",
                sceneContract = contract,
                authorizedContext = context,
                resolvedAction = ResolvedActionBody(
                    actionId = "ask_mara",
                    outcome = "FAILED",
                    check = ConfirmedCheckBody(
                        attribute = "INTELLIGENCE",
                        roll = 4,
                        modifier = 5,
                        total = 9,
                        difficulty = 12,
                        success = false,
                    ),
                    appliedEffects = listOf(Effect.ChangeLife(amount = -1)),
                ),
                lifeAfter = 9,
            ),
        )

        assertTrue(health.contains("\"status\":\"ok\"") || health.contains("\"status\": \"ok\""))
        assertTrue(interpretation is BackendCall.Ready)
        assertTrue(narration is BackendCall.Ready)
        val interpreted = (interpretation as BackendCall.Ready).body
        val narrated = (narration as BackendCall.Ready).body
        assertEquals("req-1", interpreted.requestId)
        assertEquals("rev-1", interpreted.stateRevision)
        assertEquals("plaza_arrival", interpreted.sceneContractId)
        assertEquals("req-1", narrated.requestId)
        assertEquals("rev-1", narrated.stateRevision)
        assertEquals("plaza_arrival", narrated.sceneContractId)
        assertEquals("FAILED", narrated.outcome)
        if (mode == "real") {
            assertTrue(narrated.text.isNotBlank())
            assertTrue(!health.contains("simulated-provider"))
        } else {
            assertTrue(health.contains("simulated-provider"))
            assertEquals("RECOGNIZED", interpreted.status)
            assertEquals("ask_mara", interpreted.candidateActionId)
            assertEquals("Mara turns away.", narrated.text)
        }
    }

    private fun health(base: String): String {
        val connection = URI(base.trimEnd('/') + "/health").toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 3_000
        connection.readTimeout = 3_000
        return connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
    }
}
