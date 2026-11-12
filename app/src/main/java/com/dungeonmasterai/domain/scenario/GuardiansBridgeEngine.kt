package com.dungeonmasterai.domain.scenario

import com.dungeonmasterai.domain.character.Attribute
import com.dungeonmasterai.domain.character.Character
import com.dungeonmasterai.domain.dice.CheckResult
import com.dungeonmasterai.domain.dice.Die
import com.dungeonmasterai.domain.dice.Difficulty
import com.dungeonmasterai.domain.dice.resolveCheck
import com.dungeonmasterai.domain.game.GameEvent
import com.dungeonmasterai.domain.game.GameState
import com.dungeonmasterai.domain.game.Outcome
import com.dungeonmasterai.domain.game.PlayerIntent
import com.dungeonmasterai.domain.game.RejectionReason
import com.dungeonmasterai.domain.game.Resolution

/**
 * Applies one Guardian's Bridge intent.
 * Dice rolls and every change to health, inventory, or outcome happen here.
 */
class GuardiansBridgeEngine(
    private val die: Die,
) {
    fun resolve(state: GameState, intent: PlayerIntent, narration: String? = null): Resolution {
        require(state.sceneId == GuardiansBridge.SCENE_ID) { "Wrong scene: ${state.sceneId}." }
        if (state.outcome != Outcome.ONGOING) {
            return reject(state, RejectionReason.TERMINAL)
        }
        val proposed = narration?.takeIf { it.isNotBlank() }
        return when (intent) {
            PlayerIntent.Unrecognized -> reject(state, RejectionReason.UNRECOGNIZED)
            PlayerIntent.Ambiguous -> reject(state, RejectionReason.AMBIGUOUS)
            PlayerIntent.Persuade -> persuade(state, proposed)
            PlayerIntent.Pay -> pay(state, proposed)
            PlayerIntent.Attack -> attack(state, proposed)
        }
    }

    private fun persuade(state: GameState, narration: String?): Resolution {
        val check = roll(state.character.attributes.intelligence, GuardiansBridge.PERSUADE_DIFFICULTY)
        val text = narration ?: if (check.success) GuardiansBridge.PERSUADE_SUCCESS else GuardiansBridge.PERSUADE_FAILURE
        val outcome = if (check.success) Outcome.CROSSED else Outcome.ONGOING
        return accept(
            state,
            listOf(check.toEvent(Attribute.INTELLIGENCE), GameEvent.Narration(text)),
            outcome = outcome,
        )
    }

    private fun pay(state: GameState, narration: String?): Resolution {
        val hasPurse = state.character.inventory.any { it.id == Character.COIN_PURSE_ID }
        if (!hasPurse) {
            return reject(state, RejectionReason.MISSING_ITEM)
        }
        val character = state.character.copy(
            inventory = state.character.inventory.filterNot { it.id == Character.COIN_PURSE_ID },
        )
        return accept(
            state.copy(character = character),
            listOf(
                GameEvent.ItemRemoved(Character.COIN_PURSE_ID),
                GameEvent.Narration(narration ?: GuardiansBridge.PAY_SUCCESS),
            ),
            outcome = Outcome.CROSSED,
        )
    }

    private fun attack(state: GameState, narration: String?): Resolution {
        val check = roll(state.character.attributes.strength, GuardiansBridge.ATTACK_DIFFICULTY)
        val damage = if (check.success) {
            GuardiansBridge.ATTACK_SUCCESS_DAMAGE
        } else {
            GuardiansBridge.ATTACK_FAILURE_DAMAGE
        }
        val previous = state.character.health
        val current = (previous - damage).coerceAtLeast(0)
        val outcome = when {
            current == 0 -> Outcome.DEFEATED
            check.success -> Outcome.CROSSED
            else -> Outcome.ONGOING
        }
        val text = narration ?: when (outcome) {
            Outcome.DEFEATED -> GuardiansBridge.ATTACK_DEFEATED
            Outcome.CROSSED -> GuardiansBridge.ATTACK_SUCCESS
            Outcome.ONGOING -> GuardiansBridge.ATTACK_FAILURE
        }
        return accept(
            state.copy(character = state.character.copy(health = current)),
            listOf(
                check.toEvent(Attribute.STRENGTH),
                GameEvent.HealthChanged(previous = previous, current = current),
                GameEvent.Narration(text),
            ),
            outcome = outcome,
        )
    }

    private fun roll(attribute: Int, difficulty: Difficulty): CheckResult =
        resolveCheck(die.roll(), attribute, difficulty)

    private fun reject(state: GameState, reason: RejectionReason): Resolution =
        Resolution.Rejected(
            state.copy(history = state.history + GameEvent.ActionRejected(reason)),
        )

    private fun accept(
        state: GameState,
        events: List<GameEvent>,
        outcome: Outcome,
    ): Resolution = Resolution.Accepted(
        state.copy(
            outcome = outcome,
            history = state.history + events,
        ),
    )
}

private fun CheckResult.toEvent(attribute: Attribute): GameEvent.CheckResolved =
    GameEvent.CheckResolved(
        attribute = attribute,
        roll = roll,
        modifier = modifier,
        total = total,
        difficulty = difficulty,
        success = success,
    )
