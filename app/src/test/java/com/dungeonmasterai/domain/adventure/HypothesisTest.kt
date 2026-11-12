package com.dungeonmasterai.domain.adventure

import com.dungeonmasterai.domain.adventure.state.AdventureRejection
import com.dungeonmasterai.domain.adventure.state.AdventureResolution
import com.dungeonmasterai.domain.adventure.state.Hypothesis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HypothesisTest {
    @Test
    fun aFalseHypothesisLeavesTheConfirmedStateUntouched() {
        val engine = engine(emptyList())
        val started = engine.newGame(scholar())
        val noted = engine.noteHypothesis(started, "  El vigilante escondió la campana.  ")

        assertTrue(noted is AdventureResolution.Accepted)
        val state = (noted as AdventureResolution.Accepted).state
        assertEquals(listOf("El vigilante escondió la campana."), state.hypotheses.map { it.text })
        assertEquals(started, state.copy(hypotheses = emptyList()))
        assertTrue(state.evidenceIds.isEmpty())
        assertTrue(state.deductionIds.isEmpty())
        assertEquals(null, state.endingId)

        val stillShort = engine.deduce(state, "named_conclusion")
        assertTrue(stillShort is AdventureResolution.Rejected)
        assertSame(state, (stillShort as AdventureResolution.Rejected).state)
        assertEquals(AdventureRejection.DEDUCTION_REQUIREMENTS, stillShort.reason)
    }

    @Test
    fun aBlankDuplicateOrEndedNoteReturnsTheSameState() {
        val engine = engine(emptyList())
        val started = engine.newGame(scholar())
        val noted = engine.noteHypothesis(started, "Una teoría.") as AdventureResolution.Accepted

        val blank = engine.noteHypothesis(noted.state, "   ")
        assertSame(noted.state, (blank as AdventureResolution.Rejected).state)
        assertEquals(AdventureRejection.HYPOTHESIS_INVALID, blank.reason)

        val tooLong = engine.noteHypothesis(noted.state, "a".repeat(Hypothesis.MAX_TEXT + 1))
        assertSame(noted.state, (tooLong as AdventureResolution.Rejected).state)
        assertEquals(AdventureRejection.HYPOTHESIS_INVALID, tooLong.reason)

        val duplicate = engine.noteHypothesis(noted.state, "Una teoría.")
        assertSame(noted.state, (duplicate as AdventureResolution.Rejected).state)
        assertEquals(AdventureRejection.HYPOTHESIS_DUPLICATE, duplicate.reason)

        val ended = noted.state.copy(endingId = "failure")
        val afterEnding = engine.noteHypothesis(ended, "Otra teoría.")
        assertSame(ended, (afterEnding as AdventureResolution.Rejected).state)
        assertEquals(AdventureRejection.TERMINAL, afterEnding.reason)

        val dead = noted.state.copy(character = noted.state.character.copy(health = 0))
        val afterDeath = engine.noteHypothesis(dead, "Otra teoría.")
        assertSame(dead, (afterDeath as AdventureResolution.Rejected).state)
        assertEquals(AdventureRejection.TERMINAL, afterDeath.reason)
    }
}
