package com.dungeonmasterai.domain.scenario

import com.dungeonmasterai.domain.character.CharacterClass
import com.dungeonmasterai.domain.dice.ScriptedDie
import com.dungeonmasterai.domain.game.GameEvent
import com.dungeonmasterai.domain.game.GameState
import com.dungeonmasterai.domain.game.Outcome
import com.dungeonmasterai.domain.game.PlayerIntent
import com.dungeonmasterai.domain.game.RejectionReason
import com.dungeonmasterai.domain.game.Resolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardiansBridgeEngineTest {
    @Test
    fun persuadeSucceedsOnTheExactThresholdAndCrossesWithoutDamage() {
        val result = resolve(CharacterClass.SCHOLAR, PlayerIntent.Persuade, roll = 7)

        val check = result.state.history.filterIsInstance<GameEvent.CheckResolved>().single()
        assertEquals(12, check.total)
        assertTrue(check.success)
        assertTrue(result is Resolution.Accepted)
        assertEquals(Outcome.CROSSED, result.state.outcome)
        assertEquals(10, result.state.character.health)
        assertTrue(result.state.character.inventory.any { it.id == "coin_purse" })
    }

    @Test
    fun persuadeOneBelowTheThresholdStaysOnTheBridge() {
        val before = opening(CharacterClass.SCHOLAR)
        val result = engine(6).resolve(before, PlayerIntent.Persuade)

        val check = result.state.history.filterIsInstance<GameEvent.CheckResolved>().single()
        assertEquals(11, check.total)
        assertFalse(check.success)
        assertEquals(Outcome.ONGOING, result.state.outcome)
        assertEquals(before.character, result.state.character)
    }

    @Test
    fun payingWithThePurseRemovesItAndCrossesWithoutARoll() {
        val result = engine().resolve(opening(CharacterClass.WARRIOR), PlayerIntent.Pay)

        assertTrue(result is Resolution.Accepted)
        assertEquals(Outcome.CROSSED, result.state.outcome)
        assertTrue(result.state.character.inventory.none { it.id == "coin_purse" })
        assertTrue(result.state.history.none { it is GameEvent.CheckResolved })
    }

    @Test
    fun anAcceptedProposalReplacesTheFixedLineAndStillRemovesThePurse() {
        val result = engine().resolve(
            opening(CharacterClass.WARRIOR),
            PlayerIntent.Pay,
            narration = "The guardian pockets the coins.",
        )

        assertTrue(result is Resolution.Accepted)
        assertEquals(Outcome.CROSSED, result.state.outcome)
        assertTrue(result.state.character.inventory.none { it.id == "coin_purse" })
        assertEquals(10, result.state.character.health)
        assertEquals(
            "The guardian pockets the coins.",
            result.state.history.filterIsInstance<GameEvent.Narration>().last().text,
        )
    }

    @Test
    fun aBlankProposalKeepsTheFixedLine() {
        val result = engine().resolve(opening(CharacterClass.WARRIOR), PlayerIntent.Pay, narration = "  ")

        assertEquals(
            GuardiansBridge.PAY_SUCCESS,
            result.state.history.filterIsInstance<GameEvent.Narration>().last().text,
        )
    }

    @Test
    fun payingWithoutThePurseChangesNothingButTheRejection() {
        val before = opening(CharacterClass.WARRIOR).withoutPurse()
        val result = engine().resolve(before, PlayerIntent.Pay, narration = "The guardian pockets the coins.")

        assertRejected(before, result, RejectionReason.MISSING_ITEM)
        assertTrue(result.state.history.none { it is GameEvent.Narration && it.text.contains("pockets") })
    }

    @Test
    fun aSecondPaymentAfterCrossingIsTerminal() {
        val paid = engine().resolve(opening(CharacterClass.WARRIOR), PlayerIntent.Pay).state
        val result = engine().resolve(paid, PlayerIntent.Pay)

        assertRejected(paid, result, RejectionReason.TERMINAL)
        assertTrue(result.state.character.inventory.none { it.id == "coin_purse" })
    }

    @Test
    fun attackSucceedsOnTheExactThresholdDealsOneDamageAndCrosses() {
        val result = resolve(CharacterClass.WARRIOR, PlayerIntent.Attack, roll = 12)

        val check = result.state.history.filterIsInstance<GameEvent.CheckResolved>().single()
        assertEquals(16, check.total)
        assertTrue(check.success)
        assertEquals(Outcome.CROSSED, result.state.outcome)
        assertEquals(9, result.state.character.health)
    }

    @Test
    fun attackOneBelowTheThresholdDealsFourDamageAndStays() {
        val result = resolve(CharacterClass.WARRIOR, PlayerIntent.Attack, roll = 11)

        val check = result.state.history.filterIsInstance<GameEvent.CheckResolved>().single()
        assertEquals(15, check.total)
        assertFalse(check.success)
        assertEquals(Outcome.ONGOING, result.state.outcome)
        assertEquals(6, result.state.character.health)
    }

    @Test
    fun aSuccessfulBlowThatDropsHealthToZeroIsADefeat() {
        val before = GuardiansBridge.opening("Ada", CharacterClass.WARRIOR, health = 1)
        val result = engine(12).resolve(before, PlayerIntent.Attack)

        assertEquals(Outcome.DEFEATED, result.state.outcome)
        assertEquals(0, result.state.character.health)
    }

    @Test
    fun failedDamageClampsAtZero() {
        val before = GuardiansBridge.opening("Ada", CharacterClass.WARRIOR, health = 2)
        val result = engine(11).resolve(before, PlayerIntent.Attack)

        assertEquals(Outcome.DEFEATED, result.state.outcome)
        assertEquals(0, result.state.character.health)
    }

    @Test
    fun terminalStatesRejectFurtherActionsWithoutRolling() {
        val crossed = engine().resolve(opening(CharacterClass.WARRIOR), PlayerIntent.Pay).state
        val defeated = engine(11).resolve(
            GuardiansBridge.opening("Ada", CharacterClass.WARRIOR, health = 2),
            PlayerIntent.Attack,
        ).state

        assertRejected(crossed, engine().resolve(crossed, PlayerIntent.Attack), RejectionReason.TERMINAL)
        assertRejected(defeated, engine().resolve(defeated, PlayerIntent.Persuade), RejectionReason.TERMINAL)
    }

    @Test
    fun unrecognizedAndAmbiguousIntentsDoNotChangeTheRules() {
        val before = opening(CharacterClass.SCOUT)

        assertRejected(before, engine().resolve(before, PlayerIntent.Unrecognized), RejectionReason.UNRECOGNIZED)
        assertRejected(before, engine().resolve(before, PlayerIntent.Ambiguous), RejectionReason.AMBIGUOUS)
    }

    @Test
    fun theSameRollsProduceTheSameState() {
        val first = play(listOf(7, 12))
        val second = play(listOf(7, 12))

        assertEquals(first, second)
    }

    private fun play(rolls: List<Int>): GameState {
        val engine = engine(*rolls.toIntArray())
        val persuaded = engine.resolve(opening(CharacterClass.WARRIOR), PlayerIntent.Persuade).state
        return engine.resolve(persuaded, PlayerIntent.Attack).state
    }

    private fun resolve(characterClass: CharacterClass, intent: PlayerIntent, roll: Int): Resolution =
        engine(roll).resolve(opening(characterClass), intent)

    private fun opening(characterClass: CharacterClass): GameState =
        GuardiansBridge.opening("Ada", characterClass)

    private fun engine(vararg rolls: Int): GuardiansBridgeEngine = GuardiansBridgeEngine(ScriptedDie(rolls.toList()))

    private fun GameState.withoutPurse(): GameState =
        copy(character = character.copy(inventory = emptyList()))

    private fun assertRejected(before: GameState, result: Resolution, reason: RejectionReason) {
        assertTrue(result is Resolution.Rejected)
        assertEquals(before.character, result.state.character)
        assertEquals(before.sceneId, result.state.sceneId)
        assertEquals(before.outcome, result.state.outcome)
        assertEquals(before.history + GameEvent.ActionRejected(reason), result.state.history)
    }
}
