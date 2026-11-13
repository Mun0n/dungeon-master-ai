package com.dungeonmasterai.presentation

import com.dungeonmasterai.data.MemoryAdventureGameStore
import com.dungeonmasterai.domain.adventure.FixtureWatch
import com.dungeonmasterai.domain.adventure.engine.AdventureEngine
import com.dungeonmasterai.domain.adventure.model.Adventure
import com.dungeonmasterai.domain.adventure.scholar
import com.dungeonmasterai.domain.adventure.state.AdventureLog
import com.dungeonmasterai.domain.dice.CheckResult
import com.dungeonmasterai.domain.dice.ScriptedDie
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiceRollSessionTest {
    @Test
    fun preparingACheckDoesNotRollOrChangeTheCharacter() = runBlocking {
        val store = MemoryAdventureGameStore()
        val die = ScriptedDie(listOf(15))
        val session = session(store, die)
        session.restore()
        val started = session.start(scholar())
        val items = started.record?.state?.character?.inventory?.map { it.id }

        val prepared = session.prepareText("reckless_climb")
        val parked = session.parkedCheck("reckless_climb")

        assertTrue(prepared is PreparedText.Action)
        assertEquals(2, parked?.check?.modifier)
        assertEquals(20, parked?.check?.difficulty)
        assertEquals(items, session.restore().record?.state?.character?.inventory?.map { it.id })
        val resolved = session.resolveChoice("reckless_climb", "trepar")
        val check = (resolved.record?.state?.history?.last { it is AdventureLog.ActionResolved } as AdventureLog.ActionResolved).check
        assertEquals(15, check?.roll)
        assertEquals(17, check?.total)
        assertEquals(false, check?.success)
        val revived = AdventureSession(
            AdventureEngine(withClimb(FixtureWatch.adventure()), ScriptedDie(emptyList())),
            store,
            null,
        )
        val restored = revived.restore()
        val stored = (restored.record?.state?.history?.last { it is AdventureLog.ActionResolved } as AdventureLog.ActionResolved).check
        assertEquals(15, stored?.roll)
    }

    @Test
    fun anActionWithoutACheckIsNotParked() = runBlocking {
        val session = session(MemoryAdventureGameStore(), ScriptedDie(emptyList()))
        session.restore()
        session.start(scholar())

        assertNull(session.parkedCheck("search_yard"))
        val prepared = session.prepareText("search_yard")

        assertEquals("search_yard", (prepared as PreparedText.Action).actionId)
        assertEquals(0, session.restore().record?.state?.history?.size ?: -1)
    }

    @Test
    fun theOverlayShowsOnlyTheCheckAddedByThisCommit() {
        val older = CheckResult(roll = 4, modifier = 2, total = 6, difficulty = 8, success = false)
        val added = CheckResult(roll = 18, modifier = 2, total = 20, difficulty = 8, success = true)
        val conflict = listOf(
            AdventureLog.ActionResolved("look", success = false, check = older),
            AdventureLog.ClockAdvanced(fromMinutes = 0, toMinutes = 5),
            AdventureLog.ActionResolved("leave", success = true, check = null),
        )
        val committed = conflict + AdventureLog.ActionResolved("look", success = true, check = added)

        assertEquals(added, checkAddedByThisCommit(committed, previousHistorySize = 3, actionId = "look"))
        assertNull(checkAddedByThisCommit(conflict, previousHistorySize = 2, actionId = "look"))
        assertNull(checkAddedByThisCommit(committed, previousHistorySize = committed.size, actionId = "look"))
    }

    private fun session(store: MemoryAdventureGameStore, die: ScriptedDie): AdventureSession =
        AdventureSession(AdventureEngine(withClimb(FixtureWatch.adventure()), die), store, null)

    private fun withClimb(adventure: Adventure): Adventure = adventure.copy(
        sceneContracts = adventure.sceneContracts.map { contract ->
            if (contract.id != "yard_contract") {
                contract
            } else {
                contract.copy(allowedActionIds = contract.allowedActionIds + "reckless_climb")
            }
        },
    )
}
