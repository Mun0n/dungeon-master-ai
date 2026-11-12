package com.dungeonmasterai.domain.adventure.engine

import com.dungeonmasterai.domain.adventure.model.Adventure
import com.dungeonmasterai.domain.adventure.model.PrologueBeat
import com.dungeonmasterai.domain.adventure.state.AdventureState

/**
 * The passage the player should read now.
 * A game that already has history, clues, evidence, or time does not enter.
 * A save that has passed an earlier passage resumes at the next one.
 * When every passage flag is set, there is nothing left to read.
 */
fun pendingPrologueBeat(adventure: Adventure, state: AdventureState): PrologueBeat? {
    if (adventure.prologue.isEmpty()) return null
    val pending = adventure.prologue.firstOrNull { state.flags[it.id] != true } ?: return null
    val started = adventure.prologue.any { state.flags[it.id] == true }
    if (started || isUntouchedOpening(adventure, state)) return pending
    return null
}

private fun isUntouchedOpening(adventure: Adventure, state: AdventureState): Boolean =
    state.history.isEmpty() &&
        state.minutesElapsed == 0 &&
        state.endingId == null &&
        state.locationId == adventure.startLocationId &&
        state.clueIds.isEmpty() &&
        state.evidenceIds.isEmpty() &&
        state.discoveredLocationIds == setOf(adventure.startLocationId)
