package com.dungeonmasterai.domain.adventure.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NarrativeClock(
    val startHour: Int,
    val startMinute: Int,
    val midnightEventId: String,
)

@Serializable
data class Act(
    val id: String,
    val title: String,
    val locationIds: List<String>,
)

@Serializable
data class Location(
    val id: String,
    val name: String,
    val actId: String,
)

@Serializable
data class Npc(
    val id: String,
    val name: String,
    val states: List<String>,
    val initialState: String,
)

@Serializable
data class ItemDefinition(
    val id: String,
    val name: String,
)

@Serializable
data class FlagDefinition(
    val id: String,
    @SerialName("default")
    val defaultValue: Boolean = false,
)

@Serializable
data class EvidenceDefinition(
    val id: String,
    val text: String,
)

@Serializable
data class ClueDefinition(
    val id: String,
    val text: String,
)

@Serializable
data class CheckDefinition(
    val attribute: String,
    val difficulty: Int,
)

@Serializable
data class OutcomeDefinition(
    val effects: List<Effect> = emptyList(),
    val localProse: String = "",
)

@Serializable
data class RetryRule(
    val reopenWhen: Requirement = Requirement(),
)

@Serializable
data class ActionDefinition(
    val id: String,
    val locationId: String,
    val timeCostMinutes: Int,
    val availability: Requirement = Requirement(),
    val check: CheckDefinition? = null,
    val success: OutcomeDefinition,
    val failure: OutcomeDefinition? = null,
    val retry: RetryRule? = null,
    val repeatable: Boolean = false,
)

/**
 * One optional flag selects between two closed effect lists.
 * Kotlin performs that choice. The lists themselves do not branch.
 */
@Serializable
data class StoryEvent(
    val id: String,
    val effects: List<Effect> = emptyList(),
    val whenFlag: String? = null,
    val whenFlagEffects: List<Effect> = emptyList(),
)

@Serializable
data class Ending(
    val id: String,
    val requirements: Requirement = Requirement(),
)

@Serializable
data class ClaimOption(
    val id: String,
    val gloss: String,
)

@Serializable
data class DeductionClaim(
    val id: String,
    val prompt: String,
    val options: List<ClaimOption> = emptyList(),
    val accept: List<String> = emptyList(),
    val falseValues: List<String> = emptyList(),
)

/**
 * A pair the model does not see. Kotlin uses it after the reply is well-formed.
 */
@Serializable
data class ClaimContradiction(
    val leftClaimId: String,
    val leftValue: String,
    val rightClaimId: String,
    val rightValue: String,
)

@Serializable
data class Deduction(
    val id: String,
    val evidenceIds: List<String>,
    val flag: String,
    val statement: String = "",
    val localProse: String = "",
    val claims: List<DeductionClaim> = emptyList(),
    val contradictions: List<ClaimContradiction> = emptyList(),
)

/**
 * One authored passage shown before the player can act.
 * [id] is a flag. The engine sets it when the passage is passed.
 * The passage does not roll and does not spend time.
 */
@Serializable
data class PrologueBeat(
    val id: String,
    val locationId: String,
    val prose: String,
    val effects: List<Effect> = emptyList(),
)

/**
 * Data for a future narrator. The engine does not read it when resolving rules.
 */
@Serializable
data class SceneContract(
    val id: String,
    val locationId: String,
    val purpose: String,
    val allowedActionIds: List<String> = emptyList(),
    val presentNpcIds: List<String> = emptyList(),
    val revealableInformation: List<String> = emptyList(),
    val forbiddenInformation: List<String> = emptyList(),
    val generationRequired: Boolean = false,
    val freeAction: Boolean = false,
)

@Serializable
data class Adventure(
    val schemaVersion: Int,
    val id: String,
    val title: String,
    val clock: NarrativeClock,
    val startLocationId: String,
    val deathEndingId: String,
    val acts: List<Act>,
    val locations: List<Location>,
    val npcs: List<Npc>,
    val items: List<ItemDefinition>,
    val flags: List<FlagDefinition>,
    val evidences: List<EvidenceDefinition>,
    val clues: List<ClueDefinition>,
    val actions: List<ActionDefinition>,
    val events: List<StoryEvent>,
    val endings: List<Ending>,
    val deductions: List<Deduction>,
    val sceneContracts: List<SceneContract> = emptyList(),
    val narrativeLanguage: String? = null,
    val prologue: List<PrologueBeat> = emptyList(),
)
