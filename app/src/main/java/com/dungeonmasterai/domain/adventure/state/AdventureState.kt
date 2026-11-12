package com.dungeonmasterai.domain.adventure.state

import com.dungeonmasterai.domain.adventure.model.AdventureRules
import com.dungeonmasterai.domain.adventure.model.Effect
import com.dungeonmasterai.domain.character.Character
import com.dungeonmasterai.domain.dice.CheckResult

enum class AttemptStatus {
    FAILED,
    SUCCEEDED,
    CLOSED,
}

data class AttemptRecord(
    val status: AttemptStatus,
    val worldStamp: Int,
    val relevantFacts: String = "",
)

data class ClockFace(
    val hour: Int,
    val minute: Int,
)

/**
 * A theory the player wrote down.
 * Storing it does not make it a fact and does not say whether it is right.
 */
data class Hypothesis(
    val text: String,
) {
    init {
        require(text.isNotBlank()) { "Hypothesis text must not be blank." }
        require(text.length <= MAX_TEXT) { "Hypothesis text must be at most $MAX_TEXT characters." }
        require(text == text.trim()) { "Hypothesis text must be trimmed." }
    }

    companion object {
        const val MAX_TEXT: Int = AdventureRules.PLAYER_TEXT_MAX
    }
}

fun clockFace(minutesElapsed: Int): ClockFace {
    val day = 24 * 60
    val total = AdventureRules.START_HOUR * 60 + AdventureRules.START_MINUTE + minutesElapsed
    val normalized = ((total % day) + day) % day
    return ClockFace(hour = normalized / 60, minute = normalized % 60)
}

sealed interface AdventureLog {
    data class ActionResolved(
        val actionId: String,
        val success: Boolean,
        val check: CheckResult?,
    ) : AdventureLog

    data class ClockAdvanced(
        val fromMinutes: Int,
        val toMinutes: Int,
    ) : AdventureLog

    data class MidnightFired(
        val eventId: String,
    ) : AdventureLog

    data class EndingReached(
        val endingId: String,
    ) : AdventureLog

    data class DeductionAccepted(
        val deductionId: String,
    ) : AdventureLog
}

data class AdventureState(
    val adventureId: String,
    val character: Character,
    val locationId: String,
    val minutesElapsed: Int,
    val midnightFired: Boolean,
    val flags: Map<String, Boolean>,
    val discoveredLocationIds: Set<String>,
    val evidenceIds: Set<String>,
    val clueIds: Set<String>,
    val relationships: Map<String, Int>,
    val npcStates: Map<String, String>,
    val attempts: Map<String, AttemptRecord>,
    val firedEventIds: Set<String>,
    val deductionIds: Set<String>,
    val endingId: String?,
    val worldStamp: Int,
    val history: List<AdventureLog>,
    val hypotheses: List<Hypothesis> = emptyList(),
    val narrativeSummary: String = "",
) {
    init {
        require(adventureId.isNotBlank()) { "Adventure id must not be blank." }
        require(locationId.isNotBlank()) { "Location id must not be blank." }
        require(minutesElapsed >= 0) { "Elapsed minutes must be >= 0." }
        require(worldStamp >= 0) { "World stamp must be >= 0." }
        require(hypotheses.distinctBy { it.text }.size == hypotheses.size) {
            "Hypothesis text must be unique."
        }
    }
}

enum class AdventureRejection {
    TERMINAL,
    UNKNOWN_ACTION,
    WRONG_LOCATION,
    REQUIREMENTS_NOT_MET,
    RETRY_CLOSED,
    INVENTORY_FULL,
    MISSING_ITEM,
    DUPLICATE_ITEM,
    ENDING_REQUIREMENTS,
    EFFECT_ILLEGAL,
    UNKNOWN_DEDUCTION,
    DEDUCTION_REQUIREMENTS,
    ALREADY_DEDUCED,
    HYPOTHESIS_INVALID,
    HYPOTHESIS_DUPLICATE,
}

sealed interface AdventureResolution {
    val state: AdventureState

    data class Accepted(
        override val state: AdventureState,
        val success: Boolean,
        val check: CheckResult?,
        val appliedEffects: List<Effect> = emptyList(),
    ) : AdventureResolution

    data class Rejected(
        override val state: AdventureState,
        val reason: AdventureRejection,
    ) : AdventureResolution
}
