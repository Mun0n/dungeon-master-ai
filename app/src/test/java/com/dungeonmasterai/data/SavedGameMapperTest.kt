package com.dungeonmasterai.data

import com.dungeonmasterai.domain.character.Attribute
import com.dungeonmasterai.domain.character.Attributes
import com.dungeonmasterai.domain.character.Character
import com.dungeonmasterai.domain.character.CharacterClass
import com.dungeonmasterai.domain.character.Item
import com.dungeonmasterai.domain.game.GameEvent
import com.dungeonmasterai.domain.game.GameState
import com.dungeonmasterai.domain.game.Outcome
import com.dungeonmasterai.domain.game.RejectionReason
import com.dungeonmasterai.domain.scenario.GuardiansBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SavedGameMapperTest {
    @Test
    fun roundTripKeepsEveryEventOutcomeAndInventoryOrder() {
        Outcome.entries.forEach { outcome ->
            val state = sample(outcome)
            val restored = state.toRows().toGameState()

            assertEquals(state, restored)
            assertEquals(
                listOf("coin_purse", "rope"),
                restored.character.inventory.map { it.id },
            )
            assertEquals(state.history, restored.history)
        }
    }

    @Test
    fun roundTripKeepsAnEmptyInventory() {
        val characterClass = CharacterClass.WARRIOR
        val state = GameState(
            character = Character(
                name = "Ada",
                characterClass = characterClass,
                attributes = Attributes.of(characterClass),
                health = 0,
                inventory = emptyList(),
            ),
            sceneId = GuardiansBridge.SCENE_ID,
            outcome = Outcome.DEFEATED,
            history = listOf(GameEvent.Narration("down")),
        )

        assertEquals(state, state.toRows().toGameState())
    }

    @Test
    fun illegalHealthIsNotClamped() {
        val broken = sample(Outcome.ONGOING).toRows().let { rows ->
            rows.copy(game = rows.game.copy(health = 99))
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            broken.toGameState()
        }

        assertEquals("Health must be 0..10.", error.message)
    }

    @Test
    fun emptyHistoryAndAWrongSceneAreRejected() {
        val rows = sample(Outcome.ONGOING).toRows()

        assertThrows(IllegalArgumentException::class.java) {
            rows.copy(history = emptyList()).toGameState()
        }
        assertThrows(IllegalArgumentException::class.java) {
            rows.copy(game = rows.game.copy(sceneId = "other")).toGameState()
        }
    }

    @Test
    fun anUnknownEventTypeIsRejected() {
        val rows = sample(Outcome.ONGOING).toRows()
        val unknown = rows.history.first().copy(type = "DANCE")

        assertThrows(IllegalArgumentException::class.java) {
            rows.copy(history = listOf(unknown)).toGameState()
        }
    }

    private fun sample(outcome: Outcome): GameState {
        val characterClass = CharacterClass.SCOUT
        return GameState(
            character = Character(
                name = "Ada",
                characterClass = characterClass,
                attributes = Attributes.of(characterClass),
                health = 4,
                inventory = listOf(
                    Item(id = "coin_purse", name = "Coin Purse"),
                    Item(id = "rope", name = "Rope"),
                ),
            ),
            sceneId = GuardiansBridge.SCENE_ID,
            outcome = outcome,
            history = listOf(
                GameEvent.Narration("opening"),
                GameEvent.CheckResolved(
                    attribute = Attribute.AGILITY,
                    roll = 3,
                    modifier = 4,
                    total = 7,
                    difficulty = 12,
                    success = false,
                ),
                GameEvent.HealthChanged(previous = 8, current = 4),
                GameEvent.ItemRemoved(itemId = "coin_purse"),
                GameEvent.ActionRejected(reason = RejectionReason.MISSING_ITEM),
            ),
        )
    }
}
