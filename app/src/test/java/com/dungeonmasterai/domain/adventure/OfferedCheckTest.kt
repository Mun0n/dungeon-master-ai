package com.dungeonmasterai.domain.adventure

import com.dungeonmasterai.domain.adventure.engine.AdventureEngine
import com.dungeonmasterai.domain.adventure.state.AdventureResolution
import com.dungeonmasterai.domain.dice.ScriptedDie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfferedCheckTest {
    @Test
    fun anAvailableCheckIsReadWithoutRolling() {
        val engine = AdventureEngine(FixtureWatch.adventure(), ScriptedDie(listOf(15)))
        val state = engine.newGame(scholar())

        val offer = engine.offeredCheck(state, "reckless_climb")

        assertEquals("STRENGTH", offer?.attribute)
        assertEquals(2, offer?.modifier)
        assertEquals(20, offer?.difficulty)
        val resolved = engine.resolve(state, "reckless_climb") as AdventureResolution.Accepted
        assertEquals(15, resolved.check?.roll)
        assertEquals(17, resolved.check?.total)
        assertEquals(false, resolved.check?.success)
    }

    @Test
    fun anActionWithoutACheckOffersNothing() {
        val engine = AdventureEngine(FixtureWatch.adventure(), ScriptedDie(emptyList()))
        val state = engine.newGame(scholar())

        assertNull(engine.offeredCheck(state, "search_yard"))
        assertNull(engine.offeredCheck(state, "no_such_action"))
    }
}
