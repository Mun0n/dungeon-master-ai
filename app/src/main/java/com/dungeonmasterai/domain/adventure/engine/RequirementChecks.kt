package com.dungeonmasterai.domain.adventure.engine

import com.dungeonmasterai.domain.adventure.model.ActionDefinition
import com.dungeonmasterai.domain.adventure.model.AdventureRules
import com.dungeonmasterai.domain.adventure.model.Requirement
import com.dungeonmasterai.domain.adventure.state.AdventureState
import com.dungeonmasterai.domain.adventure.state.AttemptStatus

fun Requirement.holds(state: AdventureState): Boolean {
    if (flagIds.any { state.flags[it] != true }) return false
    if (absentFlagIds.any { state.flags[it] == true }) return false
    if (itemIds.any { id -> state.character.inventory.none { it.id == id } }) return false
    if (evidenceIds.any { it !in state.evidenceIds }) return false
    if (clueIds.any { it !in state.clueIds }) return false
    if (absentClueIds.any { it in state.clueIds }) return false
    if (relationships.any { required ->
            val current = state.relationships[required.npcId]
            current == null || current < required.atLeast
        }
    ) {
        return false
    }
    if (npcStates.any { required -> state.npcStates[required.npcId] != required.state }) return false
    if (discoveredLocationIds.any { it !in state.discoveredLocationIds }) return false
    if (deductionIds.any { it !in state.deductionIds }) return false
    val minimumMinutes = atLeastMinutes
    if (minimumMinutes != null && state.minutesElapsed < minimumMinutes) return false
    if (beforeMidnight == true && state.minutesElapsed >= AdventureRules.MIDNIGHT_MINUTES) return false
    if (afterMidnight == true && state.minutesElapsed < AdventureRules.MIDNIGHT_MINUTES) return false
    return true
}

/**
 * The attempt record allows this action. Location and [Requirement.holds] are separate.
 * A failed check stays closed until its own retry facts have changed.
 */
fun ActionDefinition.canBeAttempted(state: AdventureState): Boolean {
    if (repeatable) return state.attempts[id]?.status != AttemptStatus.CLOSED
    val record = state.attempts[id] ?: return true
    if (record.status != AttemptStatus.FAILED) return false
    val retry = retry ?: return false
    if (record.relevantFacts.isEmpty()) return false
    if (!retry.reopenWhen.holds(state)) return false
    return retry.reopenWhen.relevantFacts(state) != record.relevantFacts
}

/**
 * Canonical snapshot of the facts this requirement names.
 * Time is not included. An empty requirement still returns the labeled form,
 * which is distinct from a missing snapshot.
 */
fun Requirement.relevantFacts(state: AdventureState): String {
    fun marked(ids: List<String>, held: (String) -> Boolean): String =
        ids.distinct().sorted().joinToString(",") { id -> "$id=${held(id)}" }
    val flags = marked(flagIds + absentFlagIds) { id -> state.flags[id] == true }
    val items = marked(itemIds) { id -> state.character.inventory.any { item -> item.id == id } }
    val evidence = marked(evidenceIds) { id -> id in state.evidenceIds }
    val clues = marked(clueIds + absentClueIds) { id -> id in state.clueIds }
    val relations = relationships.sortedBy { it.npcId }.joinToString(",") { required ->
        "${required.npcId}=${state.relationships[required.npcId] ?: 0}"
    }
    val npcs = npcStates.sortedBy { it.npcId }.joinToString(",") { required ->
        "${required.npcId}=${state.npcStates[required.npcId].orEmpty()}"
    }
    val discovered = marked(discoveredLocationIds) { id -> id in state.discoveredLocationIds }
    val deductions = marked(deductionIds) { id -> id in state.deductionIds }
    return "flags:$flags;items:$items;evidence:$evidence;clues:$clues;" +
        "relationships:$relations;npcs:$npcs;discovered:$discovered;deductions:$deductions"
}
