package com.dungeonmasterai.presentation

import com.dungeonmasterai.data.AdventureBackend
import com.dungeonmasterai.data.BackendCall
import com.dungeonmasterai.data.HttpAdventureBackend
import com.dungeonmasterai.data.InterpretationRequest
import com.dungeonmasterai.data.InterpretationResponse
import com.dungeonmasterai.data.MemoryAdventureGameStore
import com.dungeonmasterai.data.NarrationRequest
import com.dungeonmasterai.data.NarrationResponse
import com.dungeonmasterai.data.SceneRequest
import com.dungeonmasterai.data.SceneResponse
import com.dungeonmasterai.domain.adventure.engine.AdventureEngine
import com.dungeonmasterai.domain.adventure.load.loadAdventure
import com.dungeonmasterai.domain.adventure.narration.containsPlayerTechnicalToken
import com.dungeonmasterai.domain.adventure.scholar
import com.dungeonmasterai.domain.adventure.state.AdventureLog
import com.dungeonmasterai.domain.dice.ScriptedDie
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Live narration against the local backend.
 * Skipped unless ADVENTURE_LIVE_LLM=1. It does not read or print an API key.
 * The simulated failure in this test does not call Groq.
 */
class ValdumbriaNarrationLiveTest {
    @Test
    fun valdumbriaNarrationAgainstGroq() = runBlocking {
        assumeTrue(System.getenv("ADVENTURE_LIVE_LLM") == "1")
        val report = LiveReport("build/reports/valdumbria-narration.txt")
        try {
            simulate(report)
            val base = System.getenv("ADVENTURE_BACKEND_URL") ?: "http://127.0.0.1:8000"
            val health = health(base)
            report.line("health $health")
            assertTrue(health, health.contains("\"status\":\"ok\""))
            assertTrue(health, health.contains("\"provider\":"))
            assertTrue(health, health.contains("\"model\":"))
            play(base, report)
        } finally {
            report.write()
        }
    }

    private suspend fun simulate(report: LiveReport) {
        report.line("section simulated_failure")
        val adventure = valdumbria()
        val store = MemoryAdventureGameStore()
        val backend = CountingBackend()
        val die = ScriptedDie(listOf(12))
        val session = AdventureSession(AdventureEngine(adventure, die), store, backend)
        session.restore()
        session.start(scholar())
        repeat(3) { session.continuePrologue() }
        backend.failInterpret = true
        val unread = session.submitText("Quiero leer la carta")
        assertEquals(PlayerCopy.NARRATOR_UNAVAILABLE, unread.notice)
        assertFalse(unread.notice.orEmpty().contains("Failed to validate JSON"))
        assertEquals(3, unread.record?.revision)
        assertEquals(1, backend.interpretCalls)
        assertEquals(0, backend.narrateCalls)
        report.line(
            "interpret_failed calls=${backend.interpretCalls} narrate=${backend.narrateCalls} " +
                "revision=${unread.record?.revision} notice=${unread.notice}",
        )

        backend.failInterpret = false
        val left = session.choose("leave_the_cemetery")
        assertNull(left.notice)
        assertTrue("clue_letter" in left.record?.state?.clueIds.orEmpty())
        val looked = session.choose("look_over_the_square")
        val check = looked.record?.state?.history
            ?.filterIsInstance<AdventureLog.ActionResolved>()
            ?.last()
            ?.check
        assertEquals(12, check?.roll)
        assertEquals(2, check?.modifier)
        assertEquals(14, check?.total)
        assertEquals(8, check?.difficulty)
        assertEquals(true, check?.success)
        assertEquals(10, looked.record?.state?.character?.health)
        assertEquals(
            "Entre las sombras distingues el campanario. Alguien comenta que las campanas suenan cada medianoche.",
            looked.narration,
        )
        assertEquals(2, backend.narrateCalls)
        report.line(
            "narration_failed_after_check roll=${check?.roll} total=${check?.total} " +
                "success=${check?.success} health=${looked.record?.state?.character?.health} " +
                "narrateCalls=${backend.narrateCalls} narration=${looked.narration}",
        )
        val restored = AdventureSession(
            AdventureEngine(adventure, ScriptedDie(emptyList())),
            store,
            backend,
        ).restore()
        assertEquals(looked.narration, restored.narration)
        assertEquals(looked.record?.revision, restored.record?.revision)
        assertEquals(2, backend.narrateCalls)
        report.line("restore_did_not_repeat narrateCalls=${backend.narrateCalls}")
    }

    private suspend fun play(base: String, report: LiveReport) {
        report.line("section groq_play")
        val backend = HttpAdventureBackend(
            baseUrl = base,
            allowCleartext = true,
            connectTimeoutMs = 15_000,
            readTimeoutMs = 60_000,
        )
        val watched = WatchingBackend(backend)
        val session = AdventureSession(
            AdventureEngine(valdumbria(), ScriptedDie(List(8) { 12 })),
            MemoryAdventureGameStore(),
            watched,
        )
        session.restore()
        val opened = session.start(scholar())
        assertNull(opened.notice)
        repeat(3) { assertNull(session.continuePrologue().notice) }
        val steps = listOf(
            "leave_the_cemetery",
            "look_over_the_square",
            "go_to_tavern",
            "accuse_mara",
            "leave_the_tavern",
            "follow_elias_directions",
            "enter_the_archive",
            "read_elias_notes",
            "leave_the_archive",
            "leave_valdumbria",
        )
        var latest = opened
        steps.forEach { actionId ->
            val started = System.nanoTime()
            val shown = session.choose(actionId)
            val ms = (System.nanoTime() - started) / 1_000_000
            val text = shown.narration.orEmpty()
            val held = "evidence_elia" in shown.record?.state?.evidenceIds.orEmpty()
            val model = watched.narrations.lastOrNull()
            report.line(
                "$actionId ${ms}ms modelFailure=${model?.failure} ending=${shown.record?.state?.endingId} " +
                    "evidenceElia=$held notice=${shown.notice} text=$text",
            )
            assertNull(shown.notice)
            assertFalse(text.contains("Elia sigue viva", ignoreCase = true))
            assertFalse(text.contains("Elia is alive", ignoreCase = true))
            assertFalse(text.contains("Padre Aldren acabó con ella", ignoreCase = true))
            assertFalse(containsPlayerTechnicalToken(text))
            if (actionId != "read_elias_notes" && !held) {
                assertFalse(text.contains("Elia is dead", ignoreCase = true))
            }
            latest = shown
            if (actionId != steps.last()) delay(6_000)
        }
        val square = latest.record?.state?.history
            ?.filterIsInstance<AdventureLog.ActionResolved>()
            ?.first { it.actionId == "look_over_the_square" }
        assertEquals(12, square?.check?.roll)
        assertEquals(14, square?.check?.total)
        assertEquals(true, square?.check?.success)
        assertTrue("evidence_elia" in latest.record?.state?.evidenceIds.orEmpty())
        assertEquals("failure", latest.record?.state?.endingId)
        val beforeDeath = watched.narrations.first { it.actionId == "leave_the_cemetery" }
        val afterDeath = watched.narrations.first { it.actionId == "read_elias_notes" }
        val checkNarration = watched.narrations.first { it.actionId == "look_over_the_square" }
        report.line("model_before_death ${beforeDeath.ms}ms failure=${beforeDeath.failure} ${beforeDeath.text}")
        report.line("model_check ${checkNarration.ms}ms failure=${checkNarration.failure} ${checkNarration.text}")
        report.line("model_after_death ${afterDeath.ms}ms failure=${afterDeath.failure} ${afterDeath.text}")
        report.line("android_narrate_calls ${watched.narrations.size}")
        assertEquals(steps.size, watched.narrations.size)
        assertNull(beforeDeath.failure)
        assertNull(checkNarration.failure)
        assertNull(afterDeath.failure)
        assertTrue(beforeDeath.text.isNotBlank())
        assertTrue(checkNarration.text.isNotBlank())
        assertTrue(afterDeath.text.isNotBlank())
        assertTrue(watched.narrations.all { it.failure == null && it.text.isNotBlank() })
    }
}

private class CountingBackend : AdventureBackend {
    var failInterpret: Boolean = false
    var interpretCalls: Int = 0
    var narrateCalls: Int = 0

    override suspend fun interpret(request: InterpretationRequest): BackendCall<InterpretationResponse> {
        interpretCalls += 1
        if (failInterpret) return BackendCall.Failed("PROVIDER_ERROR", true)
        return BackendCall.Ready(
            InterpretationResponse(
                contractVersion = 1,
                requestId = request.requestId,
                stateRevision = request.stateRevision,
                sceneContractId = request.sceneContract.id,
                status = "RECOGNIZED",
                candidateActionId = "read_elias_letter",
                candidateActionIds = emptyList(),
            ),
        )
    }

    override suspend fun narrate(request: NarrationRequest): BackendCall<NarrationResponse> {
        narrateCalls += 1
        if (request.resolvedAction.actionId == "look_over_the_square") {
            return BackendCall.Failed("INVALID_MODEL_OUTPUT", true)
        }
        return BackendCall.Ready(
            NarrationResponse(
                contractVersion = 1,
                requestId = request.requestId,
                stateRevision = request.stateRevision,
                sceneContractId = request.sceneContract.id,
                outcome = request.resolvedAction.outcome,
                text = "La plaza sigue en calma.",
            ),
        )
    }

    override suspend fun scene(request: SceneRequest): BackendCall<SceneResponse> =
        BackendCall.Failed("PROVIDER_ERROR", true)
}

private class WatchingBackend(
    private val inner: AdventureBackend,
) : AdventureBackend {
    data class Narration(val actionId: String?, val ms: Long, val text: String, val failure: String?)

    val narrations: MutableList<Narration> = mutableListOf()

    override suspend fun interpret(request: InterpretationRequest): BackendCall<InterpretationResponse> =
        inner.interpret(request)

    override suspend fun narrate(request: NarrationRequest): BackendCall<NarrationResponse> {
        val started = System.nanoTime()
        val call = inner.narrate(request)
        val ms = (System.nanoTime() - started) / 1_000_000
        val text = (call as? BackendCall.Ready)?.body?.text.orEmpty()
        val failure = (call as? BackendCall.Failed)?.code
        narrations += Narration(request.resolvedAction.actionId, ms, text, failure)
        return call
    }

    override suspend fun scene(request: SceneRequest): BackendCall<SceneResponse> = inner.scene(request)
}

private class LiveReport(private val path: String) {
    private val lines = mutableListOf<String>()

    fun line(text: String) {
        lines += text
    }

    fun write() {
        val file = File(path)
        file.parentFile?.mkdirs()
        file.writeText(lines.joinToString(separator = "\n", postfix = "\n"))
    }
}

private fun valdumbria() = loadAdventure(
    File("src/main/assets/adventures/valdumbria.json").readText(),
)

private fun health(base: String): String {
    val connection = URI("$base/health").toURL().openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.connectTimeout = 5_000
    connection.readTimeout = 5_000
    return connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
}
