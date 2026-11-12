package com.dungeonmasterai.domain.scenario

import com.dungeonmasterai.domain.character.Character
import com.dungeonmasterai.domain.character.CharacterClass
import com.dungeonmasterai.domain.dice.Difficulty
import com.dungeonmasterai.domain.game.GameEvent
import com.dungeonmasterai.domain.game.GameState
import com.dungeonmasterai.domain.game.Outcome

object GuardiansBridge {
    const val SCENE_ID: String = "guardians_bridge"
    const val ATTACK_SUCCESS_DAMAGE: Int = 1
    const val ATTACK_FAILURE_DAMAGE: Int = 4
    val PERSUADE_DIFFICULTY: Difficulty = Difficulty.MODERATE
    val ATTACK_DIFFICULTY: Difficulty = Difficulty.HARD

    const val OPENING: String =
        "A hooded guardian blocks the stone bridge. The river below is black and fast. The guardian waits, one hand on a spear."
    const val PERSUADE_SUCCESS: String =
        "The guardian listens, then steps aside. The bridge is yours to cross."
    const val PERSUADE_FAILURE: String =
        "The guardian shakes their head. Words are not enough."
    const val PAY_SUCCESS: String =
        "You offer the coin purse. The guardian weighs it, pockets it, and lets you pass."
    const val ATTACK_SUCCESS: String =
        "You break the guardian's guard and force them off the bridge. The blow costs you blood."
    const val ATTACK_FAILURE: String =
        "The guardian turns your strike and drives the spear home."
    const val ATTACK_DEFEATED: String =
        "You fall on the stones. The bridge stays closed."

    fun opening(
        name: String,
        characterClass: CharacterClass,
        health: Int = Character.MAX_HEALTH,
    ): GameState = GameState(
        character = Character.create(name, characterClass, health),
        sceneId = SCENE_ID,
        outcome = Outcome.ONGOING,
        history = listOf(GameEvent.Narration(OPENING)),
    )
}
