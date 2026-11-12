package com.dungeonmasterai.presentation

import com.dungeonmasterai.data.MemoryAdventureGameStore
import com.dungeonmasterai.domain.adventure.engine.AdventureEngine
import com.dungeonmasterai.domain.adventure.scholar
import com.dungeonmasterai.domain.dice.ScriptedDie
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HypothesisSessionTest {
    @Test
    fun notingAHypothesisDuringThePrologueDoesNotSpendTimeOrRevealAProof() = runBlocking {
        val engine = AdventureEngine(valdumbriaDocument(), ScriptedDie(emptyList()))
        val store = MemoryAdventureGameStore()
        val session = AdventureSession(engine, store, backend = null)
        session.restore()
        session.start(scholar())

        val noted = session.noteHypothesis("  Aldren es inocente.  ")
        val state = noted.record?.state
        check(state != null)
        assertNull(noted.notice)
        assertEquals(listOf("Aldren es inocente."), state.hypotheses.map { it.text })
        assertEquals(0, state.minutesElapsed)
        assertEquals(10, state.character.health)
        assertTrue(state.evidenceIds.isEmpty())
        assertTrue(state.clueIds.isEmpty())
        assertTrue(state.history.isEmpty())
        assertEquals(0, state.worldStamp)
        assertNull(state.endingId)
        assertEquals("plaza", state.locationId)
        assertEquals(AdventurePhase.IDLE, noted.phase)

        val restored = AdventureSession(
            AdventureEngine(valdumbriaDocument(), ScriptedDie(emptyList())),
            store,
            backend = null,
        ).restore()
        assertEquals(listOf("Aldren es inocente."), restored.record?.state?.hypotheses?.map { it.text })
        assertEquals(0, restored.record?.state?.minutesElapsed)

        val continued = session.continuePrologue()
        assertEquals(engine.adventure.prologue[1].prose, continued.narration)
        assertEquals(listOf("Aldren es inocente."), continued.record?.state?.hypotheses?.map { it.text })
        assertEquals(0, continued.record?.state?.minutesElapsed)
        assertTrue(continued.record?.state?.clueIds.orEmpty().isEmpty())

        val duplicate = session.noteHypothesis("Aldren es inocente.")
        assertEquals(PlayerCopy.HYPOTHESIS_DUPLICATE, duplicate.notice)
        assertEquals(1, duplicate.record?.state?.hypotheses?.size)
        assertEquals(0, duplicate.record?.state?.minutesElapsed)
    }
}
