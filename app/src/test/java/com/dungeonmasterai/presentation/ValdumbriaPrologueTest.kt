package com.dungeonmasterai.presentation

import com.dungeonmasterai.data.AdventureBackend
import com.dungeonmasterai.data.BackendCall
import com.dungeonmasterai.data.InterpretationRequest
import com.dungeonmasterai.data.InterpretationResponse
import com.dungeonmasterai.data.MemoryAdventureGameStore
import com.dungeonmasterai.data.NarrationRequest
import com.dungeonmasterai.data.NarrationResponse
import com.dungeonmasterai.data.SceneRequest
import com.dungeonmasterai.data.SceneResponse
import com.dungeonmasterai.domain.adventure.engine.ActionAccess
import com.dungeonmasterai.domain.adventure.engine.AdventureEngine
import com.dungeonmasterai.domain.adventure.engine.pendingPrologueBeat
import com.dungeonmasterai.domain.adventure.scholar
import com.dungeonmasterai.domain.adventure.state.AdventureLog
import com.dungeonmasterai.domain.adventure.state.AdventureRejection
import com.dungeonmasterai.domain.adventure.state.AdventureResolution
import com.dungeonmasterai.domain.character.Character
import com.dungeonmasterai.domain.dice.ScriptedDie
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ValdumbriaPrologueTest {
    @Test
    fun thePrologueReachesTheCemeteryWithoutARollOrAProof() {
        val engine = AdventureEngine(valdumbriaDocument(), ScriptedDie(emptyList()))
        val adventure = engine.adventure
        val road = adventure.prologue.single { it.id == "prologue_road" }
        val grave = adventure.prologue.single { it.id == "prologue_grave" }
        assertTrue(road.prose.contains("Ven cuanto antes."))
        assertFalse(road.prose.contains("archivo", ignoreCase = true))
        assertFalse(road.prose.contains("Aldren"))
        assertFalse(road.prose.contains("muerta", ignoreCase = true))
        assertTrue(grave.prose.contains("El enterrador permanece inmóvil junto a la fosa."))
        assertTrue(grave.prose.contains("—Debajo de tus pies."))
        assertFalse(grave.prose.contains("Aldren"))
        assertEquals("Preguntar a Tomás cómo murió Elia", choiceLabel("ask_tomas"))

        var state = engine.newGame(scholar())
        val purse = state.character.inventory
        assertEquals(road.prose, pendingPrologueBeat(adventure, state)?.prose)

        state = accepted(engine, state)
        assertEquals("plaza", state.locationId)
        assertEquals(true, state.flags["prologue_road"])
        assertEquals(0, state.minutesElapsed)
        assertEquals(10, state.character.health)
        assertEquals(purse, state.character.inventory)
        assertTrue(state.evidenceIds.isEmpty())
        assertTrue(state.clueIds.isEmpty())
        assertTrue(state.history.isEmpty())
        assertEquals(setOf("plaza"), state.discoveredLocationIds)

        state = accepted(engine, state)
        assertEquals("plaza", state.locationId)
        assertEquals(true, state.flags["prologue_arrival"])
        assertTrue(state.clueIds.isEmpty())
        assertEquals(0, state.minutesElapsed)

        state = accepted(engine, state)
        assertEquals("cemetery", state.locationId)
        assertEquals(true, state.flags["prologue_grave"])
        assertEquals(true, state.flags["letter_read"])
        assertEquals(setOf("clue_letter", "clue_elia_dead"), state.clueIds)
        assertEquals(setOf("plaza", "tavern", "cemetery"), state.discoveredLocationIds)
        assertTrue(state.evidenceIds.isEmpty())
        assertTrue(state.character.inventory.none { it.id == "iron_pin" })
        assertEquals(purse, state.character.inventory)
        assertEquals(10, state.character.health)
        assertEquals(0, state.minutesElapsed)
        assertTrue(state.history.isEmpty())
        assertEquals("22:00", clockLabel(state))
        assertNull(pendingPrologueBeat(adventure, state))
        assertEquals(
            ActionAccess.REQUIREMENTS_BLOCKED,
            engine.access(state.copy(locationId = "plaza"), "read_elias_letter"),
        )
        assertFalse(adventure.clues.single { it.id == "clue_letter" }.text.contains("archivo", ignoreCase = true))
        assertFalse(adventure.clues.single { it.id == "clue_elia_dead" }.text.contains("Aldren"))

        val finished = engine.advancePrologue(state)
        assertTrue(finished is AdventureResolution.Rejected)
        assertSame(state, (finished as AdventureResolution.Rejected).state)
        assertEquals(AdventureRejection.REQUIREMENTS_NOT_MET, finished.reason)
    }

    @Test
    fun aGameThatAlreadyStartedDoesNotEnterThePrologue() {
        val engine = AdventureEngine(valdumbriaDocument(), ScriptedDie(emptyList()))
        val started = engine.newGame(scholar()).copy(
            history = listOf(AdventureLog.ClockAdvanced(0, 0)),
        )
        val rejected = engine.advancePrologue(started)
        assertTrue(rejected is AdventureResolution.Rejected)
        assertSame(started, (rejected as AdventureResolution.Rejected).state)
        assertNull(pendingPrologueBeat(engine.adventure, started))
    }

    @Test
    fun continuingResumesTheUnreadPassageAndThenOpensTheCemetery() = runBlocking {
        val engine = AdventureEngine(valdumbriaDocument(), ScriptedDie(emptyList()))
        val store = MemoryAdventureGameStore()
        val first = AdventureSession(engine, store, ExplodingBackend())
        first.restore()
        val opened = first.start(scholar())
        val road = engine.adventure.prologue.first().prose
        assertEquals(road, opened.narration)
        assertEquals(AdventurePhase.IDLE, opened.phase)
        assertEquals(0, opened.record?.state?.minutesElapsed)

        val blocked = first.choose("read_elias_letter")
        assertEquals(PlayerCopy.ACTION_UNAVAILABLE, blocked.notice)
        assertEquals(road, blocked.narration)
        assertEquals(0, blocked.record?.state?.minutesElapsed)
        assertEquals(PlayerCopy.ACTION_UNAVAILABLE, first.submitText("Quiero leer la carta").notice)

        val arrival = first.continuePrologue()
        assertNull(arrival.notice)
        assertEquals(engine.adventure.prologue[1].prose, arrival.narration)
        assertEquals(true, arrival.record?.state?.flags?.get("prologue_road"))
        assertTrue(arrival.record?.state?.clueIds.orEmpty().isEmpty())
        assertEquals(listOf(road), arrival.record?.recentInteractions?.map { it.text })
        assertEquals(0, arrival.record?.state?.minutesElapsed)
        assertEquals(10, arrival.record?.state?.character?.health)
        assertNull(arrival.record?.pendingNarration)

        val restored = AdventureSession(
            AdventureEngine(valdumbriaDocument(), ScriptedDie(emptyList())),
            store,
            ExplodingBackend(),
        ).restore()
        assertEquals(arrival.narration, restored.narration)
        assertEquals(0, restored.record?.state?.minutesElapsed)

        val grave = restored.record?.let { record ->
            val session = AdventureSession(engine, store, ExplodingBackend())
            session.restore()
            session.continuePrologue()
        }
        check(grave != null)
        assertEquals(engine.adventure.prologue[2].prose, grave.narration)
        assertEquals("plaza", grave.record?.state?.locationId)

        val revealed = AdventureSession(engine, store, ExplodingBackend()).let { session ->
            session.restore()
            session.continuePrologue()
        }
        assertEquals("cemetery", revealed.record?.state?.locationId)
        assertEquals(setOf("clue_letter", "clue_elia_dead"), revealed.record?.state?.clueIds)
        assertTrue(revealed.record?.state?.evidenceIds.orEmpty().isEmpty())
        assertNull(revealed.narration)
        assertEquals(grave.narration, revealed.record?.recentInteractions?.lastOrNull()?.text)
        assertEquals(0, revealed.record?.state?.minutesElapsed)
        assertEquals(3, revealed.record?.recentInteractions?.size)
        assertTrue(revealed.record?.state?.character?.inventory.orEmpty().any { it.id == Character.COIN_PURSE_ID })
        assertTrue(revealed.record?.state?.character?.inventory.orEmpty().none { it.id == "iron_pin" })

        val left = AdventureSession(engine, store, backend = null).let { session ->
            session.restore()
            session.choose("leave_the_cemetery")
        }
        assertNull(left.notice)
        assertEquals("plaza", left.record?.state?.locationId)
        assertEquals(10, left.record?.state?.minutesElapsed)
    }
}

private fun accepted(
    engine: AdventureEngine,
    state: com.dungeonmasterai.domain.adventure.state.AdventureState,
): com.dungeonmasterai.domain.adventure.state.AdventureState {
    val resolution = engine.advancePrologue(state)
    check(resolution is AdventureResolution.Accepted)
    return resolution.state
}

private class ExplodingBackend : AdventureBackend {
    override suspend fun interpret(request: InterpretationRequest): BackendCall<InterpretationResponse> =
        error("The prologue must not ask for an interpretation.")

    override suspend fun narrate(request: NarrationRequest): BackendCall<NarrationResponse> =
        error("The prologue must not ask for narration.")

    override suspend fun scene(request: SceneRequest): BackendCall<SceneResponse> =
        error("The prologue must not ask for a scene.")
}
