package com.dungeonmasterai.domain.adventure.narration

import com.dungeonmasterai.domain.adventure.model.Adventure
import com.dungeonmasterai.domain.adventure.model.EFFECT_TYPE_NAMES
import com.dungeonmasterai.domain.adventure.model.Requirement

/**
 * Identifiers the player must not read. A bare word without an underscore stays out,
 * so names and ordinary prose are not banned by this list.
 */
fun technicalIdentifiers(adventure: Adventure): List<String> {
    val found = linkedSetOf<String>()
    fun add(value: String) {
        if ('_' in value) found += value
    }
    add(adventure.id)
    add(adventure.deathEndingId)
    add(adventure.startLocationId)
    add(adventure.clock.midnightEventId)
    adventure.acts.forEach { act ->
        add(act.id)
        act.locationIds.forEach(::add)
    }
    adventure.locations.forEach { location ->
        add(location.id)
        add(location.actId)
    }
    adventure.npcs.forEach { npc ->
        add(npc.id)
        add(npc.initialState)
        npc.states.forEach(::add)
    }
    adventure.items.forEach { add(it.id) }
    adventure.flags.forEach { add(it.id) }
    adventure.evidences.forEach { add(it.id) }
    adventure.clues.forEach { add(it.id) }
    adventure.actions.forEach { action ->
        add(action.id)
        add(action.locationId)
        action.check?.let { add(it.attribute) }
        addRequirement(action.availability, ::add)
        action.retry?.let { addRequirement(it.reopenWhen, ::add) }
    }
    adventure.events.forEach { event ->
        add(event.id)
        event.whenFlag?.let(::add)
    }
    adventure.endings.forEach { ending ->
        add(ending.id)
        addRequirement(ending.requirements, ::add)
    }
    adventure.deductions.forEach { deduction ->
        add(deduction.id)
        add(deduction.flag)
        deduction.evidenceIds.forEach(::add)
        deduction.claims.forEach { claim ->
            add(claim.id)
            claim.options.forEach { add(it.id) }
            claim.accept.forEach(::add)
            claim.falseValues.forEach(::add)
        }
        deduction.contradictions.forEach { pair ->
            add(pair.leftClaimId)
            add(pair.leftValue)
            add(pair.rightClaimId)
            add(pair.rightValue)
        }
    }
    adventure.sceneContracts.forEach { contract ->
        add(contract.id)
        add(contract.locationId)
        contract.allowedActionIds.forEach(::add)
        contract.presentNpcIds.forEach(::add)
    }
    EFFECT_TYPE_NAMES.forEach(::add)
    return found.toList()
}

private fun addRequirement(requirement: Requirement, add: (String) -> Unit) {
    requirement.flagIds.forEach(add)
    requirement.absentFlagIds.forEach(add)
    requirement.itemIds.forEach(add)
    requirement.evidenceIds.forEach(add)
    requirement.clueIds.forEach(add)
    requirement.absentClueIds.forEach(add)
    requirement.discoveredLocationIds.forEach(add)
    requirement.deductionIds.forEach(add)
    requirement.relationships.forEach { add(it.npcId) }
    requirement.npcStates.forEach {
        add(it.npcId)
        add(it.state)
    }
}
