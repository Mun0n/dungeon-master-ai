package com.dungeonmasterai.data

import com.dungeonmasterai.domain.adventure.state.Hypothesis
import com.dungeonmasterai.domain.character.Presentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AdventureGameMapperTest {
    @Test
    fun roundTripSerializesTheConfirmedState() {
        val state = confirmedAdventure()
        val restored = state.toRows(DEATH_ENDING, MIDNIGHT_EVENT).toAdventureState()

        assertEquals(state, restored)
        assertEquals(listOf("coin_purse", "rope"), restored.character.inventory.map { it.id })
        assertEquals(false, restored.flags["quiet"])
        assertEquals("The bell rang.", restored.narrativeSummary)
    }

    @Test
    fun roundTripKeepsHypothesesInTheOrderTheyWereNoted() {
        val state = minimalAdventure().copy(
            hypotheses = listOf(Hypothesis("Primera."), Hypothesis("Segunda.")),
        )

        val restored = state.toRows(DEATH_ENDING, MIDNIGHT_EVENT).toAdventureState()

        assertEquals(listOf("Primera.", "Segunda."), restored.hypotheses.map { it.text })
        assertEquals(state, restored)
    }

    @Test
    fun roundTripKeepsAnEmptySummaryAndEmptyHistory() {
        val state = minimalAdventure()

        assertEquals(state, state.toRows(DEATH_ENDING, MIDNIGHT_EVENT).toAdventureState())
        assertEquals("", state.toRows(DEATH_ENDING, MIDNIGHT_EVENT).toAdventureState().narrativeSummary)
    }

    @Test
    fun deathRestoresOnlyAtZeroHealth() {
        val dead = deadAdventure().toRows(DEATH_ENDING, MIDNIGHT_EVENT).toAdventureState()
        assertEquals(0, dead.character.health)
        assertEquals(DEATH_ENDING, dead.endingId)

        val alive = deadAdventure().toRows(DEATH_ENDING, MIDNIGHT_EVENT).let { rows ->
            rows.copy(game = rows.game.copy(health = 4))
        }
        val error = assertThrows(IllegalArgumentException::class.java) {
            alive.toAdventureState()
        }
        assertEquals("A death ending restores only at health 0.", error.message)
    }

    @Test
    fun healthZeroRestoresOnlyWithTheDeathEnding() {
        val rows = deadAdventure().toRows(DEATH_ENDING, MIDNIGHT_EVENT).let { saved ->
            saved.copy(game = saved.game.copy(endingId = "justice"))
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            rows.toAdventureState()
        }
        assertEquals("Health 0 restores only with the death ending.", error.message)
    }

    @Test
    fun aMismatchedMidnightFlagIsRejected() {
        val rows = confirmedAdventure().toRows(DEATH_ENDING, MIDNIGHT_EVENT).let { saved ->
            saved.copy(game = saved.game.copy(midnightFired = false))
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            rows.toAdventureState()
        }
        assertEquals("Midnight flag does not match the fired midnight event.", error.message)
    }

    @Test
    fun roundTripKeepsAFemininePresentation() {
        val base = minimalAdventure()
        val state = base.copy(
            character = base.character.copy(presentation = Presentation.FEMININE),
        )

        val restored = state.toRows(DEATH_ENDING, MIDNIGHT_EVENT).toAdventureState()

        assertEquals(Presentation.FEMININE, restored.character.presentation)
        assertEquals(state.character.attributes, restored.character.attributes)
    }

    @Test
    fun anUnknownSnapshotVersionIsRejected() {
        val rows = minimalAdventure().toRows(DEATH_ENDING, MIDNIGHT_EVENT).let { saved ->
            saved.copy(game = saved.game.copy(schemaVersion = 2))
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            rows.toAdventureState()
        }
        assertEquals("Unsupported adventure snapshot version: 2.", error.message)
    }

    @Test
    fun attributesThatDoNotMatchTheClassAreRejected() {
        val rows = minimalAdventure().toRows(DEATH_ENDING, MIDNIGHT_EVENT).let { saved ->
            saved.copy(game = saved.game.copy(strength = 5))
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            rows.toAdventureState()
        }
        assertEquals("Attributes must match the character class.", error.message)
    }

    @Test
    fun anUnknownLogTypeIsRejected() {
        val rows = minimalAdventure().toRows(DEATH_ENDING, MIDNIGHT_EVENT)
        val unknown = AdventureLogEntity(
            gameId = ADVENTURE_GAME_ID,
            position = 0,
            type = "DANCE",
            actionId = null,
            success = null,
            roll = null,
            modifier = null,
            total = null,
            difficulty = null,
            checkSuccess = null,
            fromMinutes = null,
            toMinutes = null,
            eventId = null,
            endingId = null,
            deductionId = null,
        )

        assertThrows(IllegalArgumentException::class.java) {
            rows.copy(history = listOf(unknown)).toAdventureState()
        }
    }
}
