package com.dungeonmasterai.domain.adventure.engine

import com.dungeonmasterai.domain.adventure.load.validateAdventure
import com.dungeonmasterai.domain.adventure.model.ActionDefinition
import com.dungeonmasterai.domain.adventure.model.Adventure
import com.dungeonmasterai.domain.adventure.model.AdventureRules
import com.dungeonmasterai.domain.adventure.model.Effect
import com.dungeonmasterai.domain.adventure.model.OutcomeDefinition
import com.dungeonmasterai.domain.character.Attribute
import com.dungeonmasterai.domain.character.Character
import com.dungeonmasterai.domain.character.Item
import com.dungeonmasterai.domain.dice.CheckResult
import com.dungeonmasterai.domain.dice.Die
import com.dungeonmasterai.domain.dice.Difficulty
import com.dungeonmasterai.domain.dice.OfferedCheck
import com.dungeonmasterai.domain.dice.resolveCheck
import com.dungeonmasterai.domain.adventure.state.AdventureLog
import com.dungeonmasterai.domain.adventure.state.AdventureRejection
import com.dungeonmasterai.domain.adventure.state.AdventureResolution
import com.dungeonmasterai.domain.adventure.state.AdventureState
import com.dungeonmasterai.domain.adventure.state.AttemptRecord
import com.dungeonmasterai.domain.adventure.state.AttemptStatus
import com.dungeonmasterai.domain.adventure.state.Hypothesis

/**
 * Resolves one authored adventure.
 * A rejection returns the same state instance. Dice and every rules change happen here.
 */
open class AdventureEngine(
    val adventure: Adventure,
    private val die: Die,
) {
    private val actions = adventure.actions.associateBy { it.id }
    private val events = adventure.events.associateBy { it.id }
    private val items = adventure.items.associateBy { it.id }
    private val flags = adventure.flags.associateBy { it.id }
    private val locations = adventure.locations.associateBy { it.id }
    private val npcs = adventure.npcs.associateBy { it.id }
    private val endings = adventure.endings.associateBy { it.id }
    private val deductions = adventure.deductions.associateBy { it.id }

    init {
        val errors = validateAdventure(adventure)
        check(errors.isEmpty()) { errors.joinToString("\n") }
    }

    fun newGame(character: Character): AdventureState {
        require(character.health > 0) { "A new game starts above 0 health." }
        return AdventureState(
            adventureId = adventure.id,
            character = character,
            locationId = adventure.startLocationId,
            minutesElapsed = 0,
            midnightFired = false,
            flags = adventure.flags.associate { it.id to it.defaultValue },
            discoveredLocationIds = setOf(adventure.startLocationId),
            evidenceIds = emptySet(),
            clueIds = emptySet(),
            relationships = adventure.npcs.associate { it.id to 0 },
            npcStates = adventure.npcs.associate { it.id to it.initialState },
            attempts = emptyMap(),
            firedEventIds = emptySet(),
            deductionIds = emptySet(),
            endingId = null,
            worldStamp = 0,
            history = emptyList(),
        )
    }

    fun resolve(state: AdventureState, actionId: String): AdventureResolution {
        require(state.adventureId == adventure.id) { "Wrong adventure: ${state.adventureId}." }
        if (isTerminal(state)) return reject(state, AdventureRejection.TERMINAL)
        val action = actions[actionId] ?: return reject(state, AdventureRejection.UNKNOWN_ACTION)
        if (state.locationId != action.locationId) return reject(state, AdventureRejection.WRONG_LOCATION)
        if (!action.availability.holds(state)) return reject(state, AdventureRejection.REQUIREMENTS_NOT_MET)
        if (!action.canBeAttempted(state)) return reject(state, AdventureRejection.RETRY_CLOSED)
        val check = action.check?.let { definition ->
            val attribute = Attribute.valueOf(definition.attribute)
            val difficulty = Difficulty.entries.first { it.threshold == definition.difficulty }
            resolveCheck(
                roll = die.roll(),
                attribute = state.character.attributes.value(attribute),
                difficulty = difficulty,
            )
        }
        val success = check?.success ?: true
        val outcome = if (success) action.success else action.failure
            ?: return reject(state, AdventureRejection.EFFECT_ILLEGAL)
        val draft = Draft(state)
        val rejection = draft.applyResolved(action, outcome, success, check)
        if (rejection != null) return reject(state, rejection)
        return AdventureResolution.Accepted(
            state = draft.state,
            success = success,
            check = check,
            appliedEffects = draft.applied.toList(),
        )
    }

    /**
     * Classifies one action without rolling or changing state.
     * [ActionAccess.AVAILABLE] is the only class [resolve] will try to apply.
     */
    fun access(state: AdventureState, actionId: String): ActionAccess {
        require(state.adventureId == adventure.id) { "Wrong adventure: ${state.adventureId}." }
        val action = actions[actionId] ?: return ActionAccess.UNKNOWN
        if (state.locationId != action.locationId) return ActionAccess.UNAVAILABLE
        if (isTerminal(state) || !action.availability.holds(state) || !action.canBeAttempted(state)) {
            return ActionAccess.REQUIREMENTS_BLOCKED
        }
        return ActionAccess.AVAILABLE
    }

    /**
     * Reads the check an available action would roll.
     * It does not roll and it does not change state.
     */
    fun offeredCheck(state: AdventureState, actionId: String): OfferedCheck? {
        if (access(state, actionId) != ActionAccess.AVAILABLE) return null
        val definition = actions[actionId]?.check ?: return null
        val attribute = Attribute.valueOf(definition.attribute)
        return OfferedCheck(
            attribute = definition.attribute,
            modifier = state.character.attributes.value(attribute),
            difficulty = definition.difficulty,
        )
    }

    /**
     * Marks the next unread prologue passage.
     * It does not roll, spend minutes, or change life or inventory.
     * A game that has already started, and a prologue that is already finished, are rejected.
     */
    fun advancePrologue(state: AdventureState): AdventureResolution {
        require(state.adventureId == adventure.id) { "Wrong adventure: ${state.adventureId}." }
        if (isTerminal(state)) return reject(state, AdventureRejection.TERMINAL)
        val beat = pendingPrologueBeat(adventure, state)
            ?: return reject(state, AdventureRejection.REQUIREMENTS_NOT_MET)
        if (beat.effects.any { effect -> !effect.allowedInPrologue() }) {
            return reject(state, AdventureRejection.EFFECT_ILLEGAL)
        }
        val draft = Draft(state)
        when (val marked = draft.apply(Effect.SetFlag(key = beat.id, value = true))) {
            is EffectStep.Rejected -> return reject(state, marked.reason)
            is EffectStep.Ended -> return reject(state, AdventureRejection.EFFECT_ILLEGAL)
            is EffectStep.Continue -> Unit
        }
        when (val applied = draft.applyAll(beat.effects)) {
            is EffectStep.Rejected -> return reject(state, applied.reason)
            is EffectStep.Ended -> return reject(state, AdventureRejection.EFFECT_ILLEGAL)
            is EffectStep.Continue -> Unit
        }
        return AdventureResolution.Accepted(
            state = draft.state,
            success = true,
            check = null,
            appliedEffects = draft.applied.toList(),
        )
    }

    /**
     * Stores one player theory.
     * It does not roll, spend time, or change the confirmed world.
     * It does not say whether the theory is true.
     */
    fun noteHypothesis(state: AdventureState, text: String): AdventureResolution {
        require(state.adventureId == adventure.id) { "Wrong adventure: ${state.adventureId}." }
        if (isTerminal(state)) return reject(state, AdventureRejection.TERMINAL)
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > Hypothesis.MAX_TEXT) {
            return reject(state, AdventureRejection.HYPOTHESIS_INVALID)
        }
        if (state.hypotheses.any { it.text == trimmed }) {
            return reject(state, AdventureRejection.HYPOTHESIS_DUPLICATE)
        }
        return AdventureResolution.Accepted(
            state = state.copy(hypotheses = state.hypotheses + Hypothesis(trimmed)),
            success = true,
            check = null,
        )
    }

    open fun deduce(state: AdventureState, deductionId: String): AdventureResolution {
        require(state.adventureId == adventure.id) { "Wrong adventure: ${state.adventureId}." }
        if (isTerminal(state)) return reject(state, AdventureRejection.TERMINAL)
        val deduction = deductions[deductionId] ?: return reject(state, AdventureRejection.UNKNOWN_DEDUCTION)
        if (deductionId in state.deductionIds) return reject(state, AdventureRejection.ALREADY_DEDUCED)
        if (deduction.evidenceIds.any { it !in state.evidenceIds }) {
            return reject(state, AdventureRejection.DEDUCTION_REQUIREMENTS)
        }
        if (deduction.flag !in flags) return reject(state, AdventureRejection.EFFECT_ILLEGAL)
        val next = state.copy(
            deductionIds = state.deductionIds + deductionId,
            flags = state.flags + (deduction.flag to true),
            history = state.history + AdventureLog.DeductionAccepted(deductionId),
        )
        return AdventureResolution.Accepted(state = next, success = true, check = null)
    }

    private fun isTerminal(state: AdventureState): Boolean =
        state.endingId != null || state.character.health == 0

    private fun reject(state: AdventureState, reason: AdventureRejection): AdventureResolution =
        AdventureResolution.Rejected(state = state, reason = reason)

    private fun Draft.applyResolved(
        action: ActionDefinition,
        outcome: OutcomeDefinition,
        success: Boolean,
        check: CheckResult?,
    ): AdventureRejection? {
        val started = state
        val nextMinutes = started.minutesElapsed.toLong() + action.timeCostMinutes.toLong()
        if (nextMinutes !in 0..Int.MAX_VALUE.toLong()) return AdventureRejection.EFFECT_ILLEGAL
        state = state.copy(minutesElapsed = nextMinutes.toInt())
        val crossedMidnight = !started.midnightFired &&
            started.minutesElapsed < AdventureRules.MIDNIGHT_MINUTES &&
            state.minutesElapsed >= AdventureRules.MIDNIGHT_MINUTES
        val midnight = if (crossedMidnight) {
            state = state.copy(midnightFired = true)
            fire(adventure.clock.midnightEventId)
        } else {
            EffectStep.Continue
        }
        val applied = when (midnight) {
            is EffectStep.Rejected -> return midnight.reason
            is EffectStep.Ended -> EffectStep.Ended
            is EffectStep.Continue -> applyAll(outcome.effects)
        }
        if (applied is EffectStep.Rejected) return applied.reason
        val currentAttempt = state.attempts[action.id]
        val status = when {
            currentAttempt?.status == AttemptStatus.CLOSED -> AttemptStatus.CLOSED
            success -> AttemptStatus.SUCCEEDED
            else -> AttemptStatus.FAILED
        }
        val facts = if (status == AttemptStatus.FAILED) {
            action.retry?.reopenWhen?.relevantFacts(state).orEmpty()
        } else {
            ""
        }
        state = state.copy(
            attempts = state.attempts + (action.id to AttemptRecord(status, state.worldStamp, facts)),
        )
        val log = mutableListOf<AdventureLog>(
            AdventureLog.ActionResolved(action.id, success, check),
        )
        if (state.minutesElapsed != started.minutesElapsed) {
            log += AdventureLog.ClockAdvanced(started.minutesElapsed, state.minutesElapsed)
        }
        if (crossedMidnight) log += AdventureLog.MidnightFired(adventure.clock.midnightEventId)
        if (started.endingId == null && state.endingId != null) {
            log += AdventureLog.EndingReached(state.endingId ?: return AdventureRejection.EFFECT_ILLEGAL)
        }
        state = state.copy(history = state.history + log)
        return null
    }

    private fun Draft.fire(eventId: String): EffectStep {
        if (eventId in state.firedEventIds) return EffectStep.Continue
        val event = events[eventId] ?: return EffectStep.Rejected(AdventureRejection.EFFECT_ILLEGAL)
        val effects = if (event.whenFlag != null && state.flags[event.whenFlag] == true) {
            event.whenFlagEffects
        } else {
            event.effects
        }
        state = state.copy(firedEventIds = state.firedEventIds + event.id)
        return applyAll(effects)
    }

    private fun Draft.applyAll(effects: List<Effect>): EffectStep {
        for (effect in effects) {
            when (val step = apply(effect)) {
                is EffectStep.Rejected -> return step
                is EffectStep.Ended -> return step
                is EffectStep.Continue -> Unit
            }
        }
        return EffectStep.Continue
    }

    private fun Draft.apply(effect: Effect): EffectStep {
        if (effect is Effect.TriggerEvent) return trigger(effect)
        val endingBefore = state.endingId
        val rejection = when (effect) {
            is Effect.SetFlag -> setFlag(effect)
            is Effect.ChangeLife -> changeLife(effect)
            is Effect.AddItem -> addItem(effect)
            is Effect.RemoveItem -> removeItem(effect)
            is Effect.DiscoverLocation -> discover(effect)
            is Effect.MoveTo -> moveTo(effect)
            is Effect.SetRelationship -> setRelationship(effect)
            is Effect.AddEvidence -> addEvidence(effect)
            is Effect.AddClue -> addClue(effect)
            is Effect.SetNpcState -> setNpcState(effect)
            is Effect.RecordAttempt -> recordAttempt(effect)
            is Effect.TriggerEvent -> return EffectStep.Rejected(AdventureRejection.EFFECT_ILLEGAL)
            is Effect.SetEnding -> setEnding(effect)
        }
        if (rejection != null) return EffectStep.Rejected(rejection)
        if (effect !is Effect.SetEnding || state.endingId != endingBefore) {
            applied += effect
        }
        if (state.endingId == null && state.character.health == 0) {
            state = state.copy(endingId = adventure.deathEndingId)
            return EffectStep.Ended
        }
        if (effect is Effect.SetEnding && state.endingId != null) return EffectStep.Ended
        return EffectStep.Continue
    }

    private fun Draft.setFlag(effect: Effect.SetFlag): AdventureRejection? {
        if (effect.key !in flags) return AdventureRejection.EFFECT_ILLEGAL
        val changed = state.flags[effect.key] != effect.value
        advance(state.copy(flags = state.flags + (effect.key to effect.value)), changed)
        return null
    }

    private fun Draft.changeLife(effect: Effect.ChangeLife): AdventureRejection? {
        val next = (state.character.health.toLong() + effect.amount.toLong())
            .coerceIn(0L, Character.MAX_HEALTH.toLong())
            .toInt()
        advance(state.copy(character = state.character.copy(health = next)), advanced = false)
        return null
    }

    private fun Draft.addItem(effect: Effect.AddItem): AdventureRejection? {
        val definition = items[effect.itemId] ?: return AdventureRejection.EFFECT_ILLEGAL
        if (state.character.inventory.any { it.id == definition.id }) return AdventureRejection.DUPLICATE_ITEM
        if (state.character.inventory.size >= Character.MAX_INVENTORY) return AdventureRejection.INVENTORY_FULL
        val inventory = state.character.inventory + Item(id = definition.id, name = definition.name)
        advance(state.copy(character = state.character.copy(inventory = inventory)), advanced = true)
        return null
    }

    private fun Draft.removeItem(effect: Effect.RemoveItem): AdventureRejection? {
        if (effect.itemId !in items) return AdventureRejection.EFFECT_ILLEGAL
        if (state.character.inventory.none { it.id == effect.itemId }) return AdventureRejection.MISSING_ITEM
        val inventory = state.character.inventory.filterNot { it.id == effect.itemId }
        advance(state.copy(character = state.character.copy(inventory = inventory)), advanced = true)
        return null
    }

    private fun Draft.discover(effect: Effect.DiscoverLocation): AdventureRejection? {
        if (effect.locationId !in locations) return AdventureRejection.EFFECT_ILLEGAL
        val changed = effect.locationId !in state.discoveredLocationIds
        advance(
            state.copy(discoveredLocationIds = state.discoveredLocationIds + effect.locationId),
            changed,
        )
        return null
    }

    private fun Draft.moveTo(effect: Effect.MoveTo): AdventureRejection? {
        if (effect.locationId !in locations) return AdventureRejection.EFFECT_ILLEGAL
        val discovered = state.discoveredLocationIds + effect.locationId
        val changed = state.locationId != effect.locationId || discovered != state.discoveredLocationIds
        advance(
            state.copy(locationId = effect.locationId, discoveredLocationIds = discovered),
            changed,
        )
        return null
    }

    private fun Draft.setRelationship(effect: Effect.SetRelationship): AdventureRejection? {
        if (effect.npcId !in npcs) return AdventureRejection.EFFECT_ILLEGAL
        val changed = state.relationships[effect.npcId] != effect.value
        advance(
            state.copy(relationships = state.relationships + (effect.npcId to effect.value)),
            changed,
        )
        return null
    }

    private fun Draft.addEvidence(effect: Effect.AddEvidence): AdventureRejection? {
        if (effect.evidenceId !in adventure.evidences.map { it.id }) return AdventureRejection.EFFECT_ILLEGAL
        val changed = effect.evidenceId !in state.evidenceIds
        advance(state.copy(evidenceIds = state.evidenceIds + effect.evidenceId), changed)
        return null
    }

    private fun Draft.addClue(effect: Effect.AddClue): AdventureRejection? {
        if (effect.clueId !in adventure.clues.map { it.id }) return AdventureRejection.EFFECT_ILLEGAL
        val changed = effect.clueId !in state.clueIds
        advance(state.copy(clueIds = state.clueIds + effect.clueId), changed)
        return null
    }

    private fun Draft.setNpcState(effect: Effect.SetNpcState): AdventureRejection? {
        val npc = npcs[effect.npcId] ?: return AdventureRejection.EFFECT_ILLEGAL
        if (effect.state !in npc.states) return AdventureRejection.EFFECT_ILLEGAL
        val changed = state.npcStates[effect.npcId] != effect.state
        advance(state.copy(npcStates = state.npcStates + (effect.npcId to effect.state)), changed)
        return null
    }

    private fun Draft.recordAttempt(effect: Effect.RecordAttempt): AdventureRejection? {
        if (effect.actionId !in actions) return AdventureRejection.EFFECT_ILLEGAL
        advance(
            state.copy(
                attempts = state.attempts + (
                    effect.actionId to AttemptRecord(AttemptStatus.CLOSED, state.worldStamp)
                    ),
            ),
            advanced = false,
        )
        return null
    }

    private fun Draft.trigger(effect: Effect.TriggerEvent): EffectStep {
        if (effect.eventId == adventure.clock.midnightEventId) {
            return EffectStep.Rejected(AdventureRejection.EFFECT_ILLEGAL)
        }
        applied += effect
        return fire(effect.eventId)
    }

    private fun Draft.setEnding(effect: Effect.SetEnding): AdventureRejection? {
        if (effect.endingId == adventure.deathEndingId) return AdventureRejection.EFFECT_ILLEGAL
        if (state.endingId != null) return null
        val ending = endings[effect.endingId] ?: return AdventureRejection.EFFECT_ILLEGAL
        if (!ending.requirements.holds(state)) return AdventureRejection.ENDING_REQUIREMENTS
        advance(state.copy(endingId = ending.id), advanced = false)
        return null
    }

    private fun Draft.advance(next: AdventureState, advanced: Boolean) {
        state = if (advanced) next.copy(worldStamp = next.worldStamp + 1) else next
    }

    private class Draft(var state: AdventureState) {
        val applied: MutableList<Effect> = mutableListOf()
    }
}

enum class ActionAccess {
    AVAILABLE,
    REQUIREMENTS_BLOCKED,
    UNAVAILABLE,
    UNKNOWN,
}

private fun Effect.allowedInPrologue(): Boolean = when (this) {
    is Effect.SetFlag,
    is Effect.AddClue,
    is Effect.DiscoverLocation,
    is Effect.MoveTo,
    -> true
    else -> false
}

private sealed interface EffectStep {
    data object Continue : EffectStep
    data object Ended : EffectStep
    data class Rejected(val reason: AdventureRejection) : EffectStep
}
