package com.dungeonmasterai.presentation

import com.dungeonmasterai.data.AdventureBackend
import com.dungeonmasterai.data.AdventureCommit
import com.dungeonmasterai.data.BackendCall
import com.dungeonmasterai.data.InterpretationRequest
import com.dungeonmasterai.data.InterpretationResponse
import com.dungeonmasterai.data.MemoryAdventureGameStore
import com.dungeonmasterai.data.NarrationRequest
import com.dungeonmasterai.data.NarrationResponse
import com.dungeonmasterai.data.SceneRequest
import com.dungeonmasterai.data.SceneResponse
import com.dungeonmasterai.domain.adventure.engine.AdventureEngine
import com.dungeonmasterai.domain.adventure.load.loadAdventure
import com.dungeonmasterai.domain.adventure.scholar
import com.dungeonmasterai.domain.adventure.state.AdventureLog
import com.dungeonmasterai.domain.dice.ScriptedDie
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NarrationContinuityTest {
    @Test
    fun aMoveUsesTheDestinationAndACheckKeepsItsResult() = runBlocking {
        val passed = ContinuityBackend()
        val success = session(passed, rolls = listOf(12))
        success.restore()
        success.start(scholar())

        success.choose("read_elias_letter")
        val letter = passed.last()
        assertEquals("Plaza", letter.authorizedContext.locationName)
        assertEquals("22:05", letter.hora)
        assertTrue(letter.hecho.orEmpty().contains("Viejo amigo"))
        assertEquals("plaza_contract", letter.sceneContract.id)
        assertNull(letter.resolvedAction.check)

        success.choose("look_over_the_square")
        val looked = passed.last()
        assertEquals("Plaza", looked.authorizedContext.locationName)
        assertEquals("22:10", looked.hora)
        assertEquals(12, looked.resolvedAction.check?.roll)
        assertEquals(2, looked.resolvedAction.check?.modifier)
        assertEquals(14, looked.resolvedAction.check?.total)
        assertEquals(true, looked.resolvedAction.check?.success)
        assertTrue(looked.hecho.orEmpty().contains("campanario"))
        assertTrue(looked.sceneContract.purpose.contains("square"))

        success.choose("follow_elias_directions")
        val followed = passed.last()
        assertEquals("Plaza", followed.authorizedContext.locationName)
        assertEquals("22:20", followed.hora)
        assertTrue(followed.hecho.orEmpty().contains("archivo"))

        success.choose("enter_the_archive")
        val entered = passed.last()
        assertEquals("Archivo", entered.authorizedContext.locationName)
        assertEquals("22:30", entered.hora)
        assertEquals("plaza_contract", entered.sceneContract.id)
        assertTrue(entered.sceneContract.purpose.contains("archive"))
        assertTrue(entered.hecho.orEmpty().contains("Entras en el archivo"))
        assertTrue(entered.authorizedContext.presentNpcs.isEmpty())
        assertTrue(entered.sceneContract.allowedActionIds.contains("enter_the_archive"))

        val failed = ContinuityBackend()
        val miss = session(failed, rolls = listOf(1))
        miss.restore()
        miss.start(scholar())
        miss.choose("read_elias_letter")
        miss.choose("look_over_the_square")
        val missed = failed.last()
        assertEquals("22:10", missed.hora)
        assertEquals(1, missed.resolvedAction.check?.roll)
        assertEquals(3, missed.resolvedAction.check?.total)
        assertEquals(false, missed.resolvedAction.check?.success)
        assertTrue(missed.hecho.orEmpty().contains("oscura"))
        assertEquals("Plaza", missed.authorizedContext.locationName)
    }

    @Test
    fun invalidNarrationOfTheDiscoveryKeepsThePlazaAndTheAuthoredLine() = runBlocking {
        val backend = FailNarrationBackend()
        val adventure = loadAdventure(File("src/main/assets/adventures/valdumbria.json").readText())
        val engine = AdventureEngine(adventure, ScriptedDie(emptyList()))
        val store = MemoryAdventureGameStore()
        val started = engine.newGame(scholar())
        val prepared = started.copy(
            flags = started.flags + ("letter_read" to true),
            minutesElapsed = 15,
        )
        val seeded = store.commit(
            state = prepared,
            deathEndingId = engine.adventure.deathEndingId,
            midnightEventId = engine.adventure.clock.midnightEventId,
            expectedRevision = null,
            pendingNarration = null,
            sceneProse = emptyMap(),
            recentInteractions = emptyList(),
        )
        check(seeded is AdventureCommit.Saved)
        val session = AdventureSession(engine, store, backend)
        session.restore()

        val shown = session.choose("follow_elias_directions")
        val prose = "Preguntas por el edificio donde se guardan los papeles del pueblo y das con el archivo."

        assertEquals(prose, shown.narration)
        assertNull(shown.notice)
        assertEquals("plaza", shown.record?.state?.locationId)
        assertEquals(setOf("plaza", "archive"), shown.record?.state?.discoveredLocationIds)
        assertEquals(25, shown.record?.state?.minutesElapsed)
        assertEquals(1, resolved(shown, "follow_elias_directions"))
        assertEquals(1, backend.calls)
        assertFalse(prose.contains("estante", ignoreCase = true))
        assertFalse(prose.contains("dentro", ignoreCase = true))
        assertFalse(prose.contains("umbral", ignoreCase = true))

        val again = session.choose("follow_elias_directions")

        assertEquals(PlayerCopy.ACTION_UNAVAILABLE, again.notice)
        assertEquals(25, again.record?.state?.minutesElapsed)
        assertEquals(setOf("plaza", "archive"), again.record?.state?.discoveredLocationIds)
        assertEquals(1, resolved(again, "follow_elias_directions"))
        assertEquals(1, backend.calls)

        val restored = AdventureSession(
            AdventureEngine(adventure, ScriptedDie(emptyList())),
            store,
            backend,
        ).restore()

        assertEquals(prose, restored.narration)
        assertEquals("plaza", restored.record?.state?.locationId)
        assertEquals(1, backend.calls)
    }

    private fun resolved(shown: AdventureSessionSnapshot, actionId: String): Int =
        shown.record?.state?.history.orEmpty().count { entry ->
            entry is AdventureLog.ActionResolved && entry.actionId == actionId
        }

    private fun session(backend: ContinuityBackend, rolls: List<Int>): AdventureSession {
        val adventure = loadAdventure(File("src/main/assets/adventures/valdumbria.json").readText())
        val engine = AdventureEngine(adventure, ScriptedDie(rolls))
        val store = MemoryAdventureGameStore()
        val started = engine.newGame(scholar()).copy(
            history = listOf(AdventureLog.ClockAdvanced(0, 0)),
        )
        val saved = runBlocking {
            store.commit(
                state = started,
                deathEndingId = engine.adventure.deathEndingId,
                midnightEventId = engine.adventure.clock.midnightEventId,
                expectedRevision = null,
                pendingNarration = null,
                sceneProse = emptyMap(),
                recentInteractions = emptyList(),
            )
        }
        check(saved is AdventureCommit.Saved)
        return AdventureSession(engine, store, backend)
    }
}

private class FailNarrationBackend : AdventureBackend {
    var calls: Int = 0

    override suspend fun interpret(request: InterpretationRequest): BackendCall<InterpretationResponse> =
        BackendCall.Failed("PROVIDER_ERROR", true)

    override suspend fun narrate(request: NarrationRequest): BackendCall<NarrationResponse> {
        calls += 1
        return BackendCall.Failed("INVALID_MODEL_OUTPUT", true)
    }

    override suspend fun scene(request: SceneRequest): BackendCall<SceneResponse> =
        BackendCall.Failed("PROVIDER_ERROR", true)
}

private class ContinuityBackend : AdventureBackend {
    private var latest: NarrationRequest? = null

    fun last(): NarrationRequest = latest ?: error("No narration was requested.")

    override suspend fun interpret(request: InterpretationRequest): BackendCall<InterpretationResponse> =
        BackendCall.Failed("PROVIDER_ERROR", true)

    override suspend fun narrate(request: NarrationRequest): BackendCall<NarrationResponse> {
        latest = request
        return BackendCall.Ready(
            NarrationResponse(
                contractVersion = 1,
                requestId = request.requestId,
                stateRevision = request.stateRevision,
                sceneContractId = request.sceneContract.id,
                outcome = request.resolvedAction.outcome,
                text = "La escena sigue en calma.",
            ),
        )
    }

    override suspend fun scene(request: SceneRequest): BackendCall<SceneResponse> =
        BackendCall.Failed("PROVIDER_ERROR", true)
}
