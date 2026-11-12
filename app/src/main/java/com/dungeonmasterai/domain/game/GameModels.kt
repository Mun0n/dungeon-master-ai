package com.dungeonmasterai.domain.game

import com.dungeonmasterai.domain.character.Attribute
import com.dungeonmasterai.domain.character.Character

sealed interface PlayerIntent {
    data object Persuade : PlayerIntent

    data object Pay : PlayerIntent

    data object Attack : PlayerIntent

    data object Unrecognized : PlayerIntent

    data object Ambiguous : PlayerIntent
}

enum class Outcome {
    ONGOING,
    CROSSED,
    DEFEATED,
}

enum class RejectionReason {
    TERMINAL,
    UNRECOGNIZED,
    AMBIGUOUS,
    MISSING_ITEM,
}

sealed interface GameEvent {
    data class CheckResolved(
        val attribute: Attribute,
        val roll: Int,
        val modifier: Int,
        val total: Int,
        val difficulty: Int,
        val success: Boolean,
    ) : GameEvent

    data class HealthChanged(
        val previous: Int,
        val current: Int,
    ) : GameEvent

    data class ItemRemoved(
        val itemId: String,
    ) : GameEvent

    data class ActionRejected(
        val reason: RejectionReason,
    ) : GameEvent

    data class Narration(
        val text: String,
    ) : GameEvent
}

data class GameState(
    val character: Character,
    val sceneId: String,
    val outcome: Outcome,
    val history: List<GameEvent>,
) {
    init {
        require(sceneId.isNotBlank()) { "Scene id must not be blank." }
    }
}

sealed interface Resolution {
    val state: GameState

    data class Accepted(
        override val state: GameState,
    ) : Resolution

    data class Rejected(
        override val state: GameState,
    ) : Resolution
}
