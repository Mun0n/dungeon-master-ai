package com.dungeonmasterai.presentation

import com.dungeonmasterai.data.AdventureBackend
import com.dungeonmasterai.data.AdventureGameStore
import com.dungeonmasterai.data.BackendCall
import com.dungeonmasterai.data.HttpAdventureBackend
import com.dungeonmasterai.data.InterpretationRequest
import com.dungeonmasterai.data.InterpretationResponse
import com.dungeonmasterai.data.NarrationDelivery
import com.dungeonmasterai.data.NarrationRequest
import com.dungeonmasterai.data.NarrationResponse
import com.dungeonmasterai.data.RoomAdventureGameStore
import com.dungeonmasterai.data.openMemoryDatabase
import com.dungeonmasterai.data.toContextBody
import com.dungeonmasterai.data.toContractBody
import com.dungeonmasterai.domain.adventure.FixtureWatch
import com.dungeonmasterai.domain.adventure.engine.AdventureEngine
import com.dungeonmasterai.domain.adventure.narration.narrationView
import com.dungeonmasterai.domain.adventure.scholar
import com.dungeonmasterai.domain.dice.ScriptedDie
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Live check against a running FastAPI process and a real model.
 * Skipped unless ADVENTURE_LIVE_LLM=1. It does not read or print an API key.
 */
class FixtureWatchLiveLlmTest {
    @Test
    fun fixtureWatchAgainstARealModel() = runBlocking {
        assumeTrue(System.getenv("ADVENTURE_LIVE_LLM") == "1")
        val base = System.getenv("ADVENTURE_BACKEND_URL") ?: "http://127.0.0.1:8000"
        val report = Report()
        try {
            runLive(base, report)
        } finally {
            report.write()
        }
    }

    private suspend fun runLive(base: String, report: Report) {
        val health = health(base)
        report.line("health $health")
        assertTrue(health, health.contains("gpt-oss-20b"))
        val backend = RecordingBackend(
            HttpAdventureBackend(
                baseUrl = base,
                allowCleartext = true,
                connectTimeoutMs = 5_000,
                readTimeoutMs = 60_000,
            ),
        )
        val engine = AdventureEngine(FixtureWatch.adventure(), ScriptedDie(listOf(1)))
        val opening = engine.newGame(scholar())
        val view = checkNotNull(engine.narrationView(opening, emptyList()))

        report.line("section interpretation")
        val phrases = listOf(
            "valid" to "I search the yard for anything useful.",
            "ambiguous" to "I search the yard and warn the warden.",
            "unrecognized" to "I cast a fireball at the moon.",
            "unavailable" to "I open the hall door.",
        )
        val interpretations = phrases.map { (kind, phrase) ->
            val call = backend.interpret(
                InterpretationRequest(
                    contractVersion = 1,
                    requestId = "live-$kind",
                    stateRevision = "r0",
                    sceneContract = view.toContractBody(),
                    authorizedContext = view.toContextBody(),
                    playerText = phrase,
                ),
            )
            val event = backend.events.last()
            val summary = when (call) {
                is BackendCall.Failed -> "failed ${call.code} retryable=${call.retryable}"
                is BackendCall.Ready -> call.body.let { body ->
                    "status=${body.status} action=${body.candidateActionId} options=${body.candidateActionIds}"
                }
            }
            report.line("$kind ${event.ms}ms $summary phrase=${phrase}")
            call
        }
        assertTrue(interpretations.any { it is BackendCall.Ready })

        report.line("section end_to_end")
        val search = turn("search", "I search the yard for anything useful.", backend, report)
        if (search.snapshot.record?.state?.character?.inventory?.none { it.id == "brass_key" } != false) {
            val chosen = turn("search_button", null, backend, report, choose = "search_yard")
            assertEquals("brass_key", chosen.snapshot.record?.state?.character?.inventory?.last()?.id)
            report.line("search_persisted_by_button delivery=${chosen.store.load()?.pendingNarration?.delivery}")
        } else {
            report.line(
                "search_persisted_by_phrase delivery=${search.store.load()?.pendingNarration?.delivery}",
            )
        }

        val leaving = turn("leave", "I give up and leave the yard.", backend, report)
        val left = if (leaving.snapshot.record?.state?.endingId == "failure") {
            leaving
        } else {
            turn("leave_button", null, backend, report, choose = "walk_away")
        }
        assertEquals("failure", left.snapshot.record?.state?.endingId)
        report.line("leave_delivery ${left.store.load()?.pendingNarration?.delivery}")
        val restoredLeave = AdventureSession(
            AdventureEngine(FixtureWatch.adventure(), ScriptedDie(emptyList())),
            left.store,
            backend,
        ).restore()
        val callsBeforeRestore = backend.events.size
        assertEquals(left.snapshot.narration, restoredLeave.narration)
        assertEquals(callsBeforeRestore, backend.events.size)
        report.line("leave_restored_without_another_model_call ending=${restoredLeave.record?.state?.endingId}")
        report.line("leave_narration ${restoredLeave.narration}")

        val climb = playLethalClimb(backend, report)
        assertEquals(0, climb.snapshot.record?.state?.character?.health)
        assertEquals("death", climb.snapshot.record?.state?.endingId)
        assertEquals(NarrationDelivery.VALIDATED, climb.store.load()?.pendingNarration?.delivery)
        assertEquals("FAILED", climb.store.load()?.pendingNarration?.outcome)
        assertTrue(backend.events.any { it.kind == "narrate" && it.result == "ready" })
        val callsBeforeClimbRestore = backend.events.size
        val restoredClimb = AdventureSession(
            AdventureEngine(withLethalClimb(FixtureWatch.adventure()), ScriptedDie(emptyList())),
            climb.store,
            backend,
        ).restore()
        assertEquals(climb.snapshot.narration, restoredClimb.narration)
        assertEquals(0, restoredClimb.record?.state?.character?.health)
        assertEquals("death", restoredClimb.record?.state?.endingId)
        assertEquals(callsBeforeClimbRestore, backend.events.size)
        report.line("climb_restored_without_another_model_call narration=${restoredClimb.narration}")

        report.line("section client_calls")
        backend.events.forEach { event -> report.line("${event.kind} ${event.ms}ms ${event.result}") }
        val narrations = backend.events.count { it.kind == "narrate" && it.result == "ready" }
        assertTrue(narrations > 0)
        report.write()
    }

    private suspend fun playLethalClimb(backend: RecordingBackend, report: Report): Played {
        val database = openMemoryDatabase()
        val store = RoomAdventureGameStore(database.adventureGameDao())
        val session = AdventureSession(
            AdventureEngine(withLethalClimb(FixtureWatch.adventure()), ScriptedDie(listOf(1))),
            store,
            backend,
        )
        session.restore()
        session.start(scholar())
        val callsBefore = backend.events.size
        val started = System.nanoTime()
        val snapshot = session.choose("reckless_climb")
        val ms = (System.nanoTime() - started) / 1_000_000
        val state = snapshot.record?.state
        val pending = snapshot.record?.pendingNarration
        report.line(
            "climb ${ms}ms phase=${snapshot.phase} notice=${snapshot.notice} " +
                "health=${state?.character?.health} ending=${state?.endingId} " +
                "outcome=${pending?.outcome} check=${pending?.check} " +
                "effects=${pending?.appliedEffects?.map { it::class.simpleName }} " +
                "delivery=${store.load()?.pendingNarration?.delivery} narration=${snapshot.narration}",
        )
        backend.events.drop(callsBefore).forEach { event ->
            report.line("climb call ${event.kind} ${event.ms}ms ${event.result}")
        }
        return Played(database, store, snapshot)
    }

    private suspend fun turn(
        name: String,
        phrase: String?,
        backend: AdventureBackend,
        report: Report,
        choose: String? = null,
    ): Played {
        val database = openMemoryDatabase()
        val store = RoomAdventureGameStore(database.adventureGameDao())
        val session = AdventureSession(
            AdventureEngine(FixtureWatch.adventure(), ScriptedDie(listOf(1))),
            store,
            backend,
        )
        session.restore()
        session.start(scholar())
        val callsBefore = if (backend is RecordingBackend) backend.events.size else 0
        val started = System.nanoTime()
        val snapshot = if (choose != null) session.choose(choose) else session.submitText(checkNotNull(phrase))
        val ms = (System.nanoTime() - started) / 1_000_000
        val state = snapshot.record?.state
        report.line(
            "$name ${ms}ms phase=${snapshot.phase} notice=${snapshot.notice} " +
                "health=${state?.character?.health} ending=${state?.endingId} " +
                "inventory=${state?.character?.inventory?.map { it.id }} " +
                "revision=${snapshot.record?.revision} narration=${snapshot.narration}",
        )
        if (backend is RecordingBackend) {
            backend.events.drop(callsBefore).forEach { event ->
                report.line("$name call ${event.kind} ${event.ms}ms ${event.result}")
            }
        }
        return Played(database, store, snapshot)
    }

    private fun health(base: String): String {
        val connection = URI(base.trimEnd('/') + "/health").toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 3_000
        connection.readTimeout = 3_000
        return connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
    }
}

private class Played(
    val database: com.dungeonmasterai.data.AppDatabase,
    val store: AdventureGameStore,
    val snapshot: AdventureSessionSnapshot,
)

private fun withLethalClimb(adventure: com.dungeonmasterai.domain.adventure.model.Adventure) =
    adventure.copy(
        sceneContracts = adventure.sceneContracts.map { contract ->
            if (contract.id == "yard_contract") {
                contract.copy(allowedActionIds = contract.allowedActionIds + "reckless_climb")
            } else {
                contract
            }
        },
        actions = adventure.actions.map { action ->
            if (action.id == "reckless_climb") {
                action.copy(failure = action.failure?.copy(localProse = "The climb fails."))
            } else {
                action
            }
        },
    )

private class Report {
    private val lines = mutableListOf<String>()

    fun line(text: String) {
        lines += text
    }

    fun write() {
        val file = File("build/reports/live-llm.txt")
        file.parentFile.mkdirs()
        file.writeText(lines.joinToString(separator = "\n", postfix = "\n"))
    }
}

private class RecordingBackend(
    private val inner: AdventureBackend,
) : AdventureBackend {
    data class Event(val kind: String, val ms: Long, val result: String)

    val events: MutableList<Event> = mutableListOf()

    override suspend fun interpret(request: InterpretationRequest): BackendCall<InterpretationResponse> =
        time("interpret") { inner.interpret(request) }

    override suspend fun narrate(request: NarrationRequest): BackendCall<NarrationResponse> =
        time("narrate") { inner.narrate(request) }

    override suspend fun scene(request: com.dungeonmasterai.data.SceneRequest) =
        time("scene") { inner.scene(request) }

    private suspend fun <T> time(kind: String, block: suspend () -> BackendCall<T>): BackendCall<T> {
        val started = System.nanoTime()
        val call = block()
        val result = when (call) {
            is BackendCall.Ready -> "ready"
            is BackendCall.Failed -> "failed ${call.code} retryable=${call.retryable}"
        }
        events += Event(kind, (System.nanoTime() - started) / 1_000_000, result)
        return call
    }
}
