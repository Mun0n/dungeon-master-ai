package com.dungeonmasterai.domain.adventure.engine

import com.dungeonmasterai.domain.adventure.model.ActionDefinition
import com.dungeonmasterai.domain.adventure.model.Adventure
import com.dungeonmasterai.domain.adventure.model.Effect
import com.dungeonmasterai.domain.character.Character
import com.dungeonmasterai.domain.character.Item
import com.dungeonmasterai.domain.adventure.state.AdventureState

const val FEATURED_LIMIT: Int = 4
const val FEATURED_PREFERRED: Int = 3

/**
 * The buttons for one scene, and whether a written action is open.
 *
 * [actionIds] is a subset of the actions the engine can already resolve.
 * Hiding a button does not remove the action. The interpreter still sees it.
 *
 * The page prefers three distinct decisions and stops at four. A rescue that
 * expires at midnight is kept. Then the smallest set of available actions that
 * still leads to each missing proof: a direct gain, a move, a discovery, or a
 * clue that unlocks that proof. A clue some later action requires comes next,
 * then an early ending such as leaving the story, then one way out, then one
 * extra gain when the page would otherwise show fewer than three.
 *
 * Two actions that grant the same clues are not both shown. The shorter one
 * wins, and the scene contract breaks a tie.
 *
 * When the scene does not allow a written action, every available action stays
 * on the page. An offered conclusion opens the written action anyway.
 */
data class DecisionOffer(
    val actionIds: List<String>,
    val freeAction: Boolean,
)

fun decisionOffer(
    adventure: Adventure,
    state: AdventureState,
    availableActionIds: List<String>,
    sceneAllowsFreeAction: Boolean,
    conclusionOffered: Boolean,
): DecisionOffer {
    val freeAction = sceneAllowsFreeAction || conclusionOffered
    if (availableActionIds.isEmpty()) return DecisionOffer(emptyList(), freeAction)
    if (!sceneAllowsFreeAction) return DecisionOffer(availableActionIds, freeAction)
    return DecisionOffer(
        actionIds = featured(adventure, state, availableActionIds),
        freeAction = freeAction,
    )
}

private fun featured(
    adventure: Adventure,
    state: AdventureState,
    availableIds: List<String>,
): List<String> {
    val available = availableIds.mapNotNull { id -> adventure.actions.find { it.id == id } }
    val index = availableIds.withIndex().associate { it.value to it.index }
    val resolution = available.filter { it.isResolutionEnding() }.sortedBy { index[it.id] }
    if (resolution.isNotEmpty()) {
        val endings = resolution.take(FEATURED_LIMIT - 1)
        val chosen = endings.map { it.id }.toMutableList()
        if (chosen.none { id -> available.first { it.id == id }.isMove() }) {
            exitMove(available, adventure, chosen.toSet(), index)?.let { chosen += it }
        }
        return present(chosen, available, adventure, index)
    }

    val chosen = linkedSetOf<String>()
    available.filter { it.isRescue(adventure) }
        .sortedBy { index[it.id] }
        .forEach { chosen += it.id }
    cover(adventure, state, available, index).forEach { chosen += it }
    if (chosen.size < FEATURED_LIMIT) {
        gating(adventure, state, available, chosen, index).forEach { id ->
            if (chosen.size < FEATURED_LIMIT) chosen += id
        }
    }
    if (chosen.size < FEATURED_LIMIT) {
        approaches(adventure, state, available, index).forEach { id ->
            if (chosen.size < FEATURED_LIMIT) chosen += id
        }
    }
    if (chosen.size < FEATURED_LIMIT) {
        available.filter { it.isOptionalEnding() }
            .sortedBy { index[it.id] }
            .forEach { action ->
                if (chosen.size < FEATURED_LIMIT) chosen += action.id
            }
    }
    if (chosen.none { id -> available.first { it.id == id }.isMove() } && chosen.size < FEATURED_LIMIT) {
        exitMove(available, adventure, chosen, index)?.let { chosen += it }
    }
    if (chosen.size < FEATURED_PREFERRED) {
        padding(state, available, chosen, index).forEach { id ->
            if (chosen.size < FEATURED_PREFERRED) chosen += id
        }
    }
    return present(chosen.toList(), available, adventure, index)
}

private fun cover(
    adventure: Adventure,
    state: AdventureState,
    available: List<ActionDefinition>,
    index: Map<String, Int>,
): List<String> {
    val missing = adventure.deductions.flatMap { it.evidenceIds }.toSet() - state.evidenceIds
    if (missing.isEmpty()) return emptyList()
    val coveredBy = available.associate { action ->
        action.id to missing.filter { evidence ->
            reaches(action, evidence, adventure, state, available)
        }.toSet()
    }
    val uncovered = missing.toMutableSet()
    val chosen = mutableListOf<String>()
    while (uncovered.isNotEmpty()) {
        val best = available
            .filter { action -> coveredBy[action.id].orEmpty().any { it in uncovered } }
            .maxWithOrNull(
                compareBy<ActionDefinition>(
                    { coveredBy[it.id].orEmpty().count { evidence -> evidence in uncovered } },
                    { localCertainty(it, uncovered) },
                    { -it.timeCostMinutes },
                    { -(it.check?.difficulty ?: 0) },
                    { -(index[it.id] ?: 0) },
                ),
            ) ?: break
        chosen += best.id
        uncovered -= coveredBy[best.id].orEmpty()
    }
    return chosen
}

private fun reaches(
    action: ActionDefinition,
    evidenceId: String,
    adventure: Adventure,
    state: AdventureState,
    available: List<ActionDefinition>,
): Boolean {
    if (action.couldGrant(state, evidenceId, Gained())) return true
    val open = openedBy(action, adventure, state, available)
    val brought = action.gained()
    if (open.any { it.couldGrant(state, evidenceId, brought) }) return true
    return reached(action, adventure, state, open).any { arrival ->
        adventure.actions.any { it.locationId == arrival.locationId && it.couldGrant(state, evidenceId, arrival.gained) }
    }
}

/**
 * Places this action can lead to without coming back for a different choice.
 * A discovery uses what was known before the action, so its own destination stays open.
 */
private fun reached(
    action: ActionDefinition,
    adventure: Adventure,
    state: AdventureState,
    unlocked: List<ActionDefinition>,
): List<Walk> {
    val known = state.discoveredLocationIds
    val queue = ArrayDeque<Walk>()
    val seenStart = setOf(state.locationId)
    fun enqueue(dest: String, from: ActionDefinition, knownNow: Set<String>, seen: Set<String>, gained: Gained) {
        if (dest in seen || !from.canArrive(dest, knownNow)) return
        queue += Walk(dest, knownNow + dest, seen + dest, gained + from.gained())
    }
    action.arrivals().forEach { dest -> enqueue(dest, action, known, seenStart, Gained()) }
    unlocked.forEach { move ->
        move.arrivals().forEach { dest -> enqueue(dest, move, known, seenStart, action.gained()) }
    }
    val found = mutableListOf<Walk>()
    val visited = mutableSetOf<Visit>()
    while (queue.isNotEmpty()) {
        val walk = queue.removeFirst()
        if (!visited.add(Visit(walk.locationId, walk.known, walk.gained))) continue
        found += walk
        adventure.actions.filter { it.locationId == walk.locationId }.forEach { step ->
            step.arrivals().forEach { dest ->
                enqueue(dest, step, walk.known, walk.seen, walk.gained)
            }
        }
    }
    return found
}

private fun openedBy(
    action: ActionDefinition,
    adventure: Adventure,
    state: AdventureState,
    available: List<ActionDefinition>,
): List<ActionDefinition> {
    val availableIds = available.map { it.id }.toSet()
    return adventure.actions.filter { other ->
        other.locationId == action.locationId &&
            other.id !in availableIds &&
            other.availability.clueIds.any { it in action.grantedClues() } &&
            (other.isMove() || other.grantedDiscoveries().isNotEmpty() || other.grantedEvidence().isNotEmpty())
    }
}

private fun approaches(
    adventure: Adventure,
    state: AdventureState,
    available: List<ActionDefinition>,
    index: Map<String, Int>,
): List<String> {
    val goals = adventure.actions
        .filter { it.isResolutionEnding() && it.availability.holds(state) && it.locationId != state.locationId }
        .map { it.locationId }
        .toSet()
    if (goals.isEmpty()) return emptyList()
    val coveredBy = available.associate { action ->
        val open = openedBy(action, adventure, state, available)
        val places = reached(action, adventure, state, open).map { it.locationId }.toSet()
        action.id to goals.filter { it in places }.toSet()
    }
    val uncovered = goals.toMutableSet()
    val chosen = mutableListOf<String>()
    while (uncovered.isNotEmpty()) {
        val best = available
            .filter { coveredBy[it.id].orEmpty().any { goal -> goal in uncovered } }
            .maxWithOrNull(
                compareBy<ActionDefinition>(
                    { coveredBy[it.id].orEmpty().count { goal -> goal in uncovered } },
                    { -it.timeCostMinutes },
                    { -(index[it.id] ?: 0) },
                ),
            ) ?: break
        chosen += best.id
        uncovered -= coveredBy[best.id].orEmpty()
    }
    return chosen
}

private data class Gained(
    val clues: Set<String> = emptySet(),
    val discoveries: Set<String> = emptySet(),
    val flags: Map<String, Boolean> = emptyMap(),
    val items: Set<String> = emptySet(),
) {
    operator fun plus(other: Gained): Gained = Gained(
        clues = clues + other.clues,
        discoveries = discoveries + other.discoveries,
        flags = flags + other.flags,
        items = items + other.items,
    )
}

private data class Walk(
    val locationId: String,
    val known: Set<String>,
    val seen: Set<String>,
    val gained: Gained,
)

private data class Visit(
    val locationId: String,
    val known: Set<String>,
    val gained: Gained,
)

private fun localCertainty(action: ActionDefinition, uncovered: Set<String>): Int =
    if (action.check == null && action.grantedEvidence().any { it in uncovered }) 1 else 0

private fun gating(
    adventure: Adventure,
    state: AdventureState,
    available: List<ActionDefinition>,
    alreadyGranted: Set<String>,
    index: Map<String, Int>,
): List<String> {
    val granted = available
        .filter { it.id in alreadyGranted }
        .flatMap { it.grantedClues() }
        .toSet()
    val needed = adventure.actions.flatMap { it.availability.clueIds }.toSet() - state.clueIds - granted
    if (needed.isEmpty()) return emptyList()
    val open = needed.toMutableSet()
    val chosen = mutableListOf<String>()
    while (open.isNotEmpty()) {
        val best = available
            .filter { action -> action.grantedClues().any { it in open } }
            .maxWithOrNull(
                compareBy<ActionDefinition>(
                    { it.grantedClues().count { clue -> clue in open } },
                    { -it.timeCostMinutes },
                    { -(index[it.id] ?: 0) },
                ),
            ) ?: break
        chosen += best.id
        open -= best.grantedClues()
    }
    return chosen
}

private fun padding(
    state: AdventureState,
    available: List<ActionDefinition>,
    selected: Set<String>,
    index: Map<String, Int>,
): List<String> {
    val held = selected.flatMap { id ->
        val action = available.first { it.id == id }
        action.grantedClues() + action.grantedItems() + action.grantedDiscoveries() + action.grantedEvidence()
    }.toSet()
    val already = state.clueIds + state.evidenceIds + state.discoveredLocationIds +
        state.character.inventory.map { it.id }
    return available
        .filter { action ->
            action.id !in selected &&
                !action.setsEnding() &&
                !action.isOnlyFlagOrWithdrawal() &&
                (action.grantedClues() + action.grantedItems() + action.grantedDiscoveries() + action.grantedEvidence())
                    .any { it !in held && it !in already }
        }
        .sortedWith(
            compareByDescending<ActionDefinition> { it.grantedDiscoveries().count { it !in state.discoveredLocationIds } }
                .thenByDescending { it.grantedClues().size + it.grantedItems().size }
                .thenBy { it.timeCostMinutes }
                .thenBy { index[it.id] ?: 0 },
        )
        .map { it.id }
}

private fun exitMove(
    available: List<ActionDefinition>,
    adventure: Adventure,
    selected: Set<String>,
    index: Map<String, Int>,
): String? = available
    .filter { it.isMove() && it.id !in selected }
    .sortedWith(
        compareByDescending<ActionDefinition> { action ->
            action.success.effects.filterIsInstance<Effect.MoveTo>().any { it.locationId == adventure.startLocationId }
        }.thenBy { index[it.id] ?: 0 },
    )
    .firstOrNull()
    ?.id

private fun present(
    ids: List<String>,
    available: List<ActionDefinition>,
    adventure: Adventure,
    index: Map<String, Int>,
): List<String> {
    fun action(id: String) = available.first { it.id == id }
    val rescues = ids.filter { action(it).isRescue(adventure) }.sortedBy { index[it] }
    val quits = ids.filter { action(it).isOptionalEnding() }.sortedBy { index[it] }
    val moves = ids.filter { action(it).isMove() && it !in quits && it !in rescues }.sortedBy { index[it] }
    val here = ids.filter { it !in rescues && it !in quits && it !in moves }.sortedBy { index[it] }
    return (rescues + here + moves + quits).distinct()
}

private fun ActionDefinition.isRescue(adventure: Adventure): Boolean {
    if (availability.beforeMidnight != true) return false
    val watched = adventure.events.mapNotNull { it.whenFlag }.toSet()
    return success.effects.filterIsInstance<Effect.SetFlag>().any { it.value && it.key in watched }
}

private fun ActionDefinition.isResolutionEnding(): Boolean =
    setsEnding() && (availability.evidenceIds.isNotEmpty() || availability.deductionIds.isNotEmpty())

private fun ActionDefinition.isOptionalEnding(): Boolean = setsEnding() && !isResolutionEnding()

private fun ActionDefinition.setsEnding(): Boolean =
    success.effects.any { it is Effect.SetEnding }

private fun ActionDefinition.isMove(): Boolean =
    success.effects.any { it is Effect.MoveTo }

private fun ActionDefinition.isOnlyFlagOrWithdrawal(): Boolean {
    if (success.effects.isEmpty()) return false
    return grantedClues().isEmpty() &&
        grantedEvidence().isEmpty() &&
        grantedItems().isEmpty() &&
        grantedDiscoveries().isEmpty() &&
        !isMove() &&
        !setsEnding()
}

private fun ActionDefinition.couldGrant(
    state: AdventureState,
    evidenceId: String,
    gained: Gained,
): Boolean {
    if (evidenceId !in grantedEvidence()) return false
    val projected = state.with(gained)
    if (!availability.holds(projected)) return false
    return canBeAttempted(projected)
}

private fun AdventureState.with(gained: Gained): AdventureState {
    if (gained.clues.isEmpty() && gained.discoveries.isEmpty() && gained.flags.isEmpty() && gained.items.isEmpty()) {
        return this
    }
    val extra = gained.items.filter { id -> character.inventory.none { it.id == id } }
    val room = Character.MAX_INVENTORY - character.inventory.size
    val added = extra.take(room.coerceAtLeast(0)).map { Item(id = it, name = it) }
    return copy(
        clueIds = clueIds + gained.clues,
        discoveredLocationIds = discoveredLocationIds + gained.discoveries,
        flags = flags + gained.flags,
        character = character.copy(inventory = character.inventory + added),
    )
}

private fun ActionDefinition.gained(): Gained = Gained(
    clues = grantedClues(),
    discoveries = grantedDiscoveries(),
    flags = success.effects.filterIsInstance<Effect.SetFlag>().associate { it.key to it.value },
    items = grantedItems(),
)

private fun ActionDefinition.canArrive(dest: String, known: Set<String>): Boolean {
    val discovering = success.effects.filterIsInstance<Effect.DiscoverLocation>().any { it.locationId == dest }
    if (discovering && dest in known) return false
    if (dest in availability.discoveredLocationIds && dest !in known) return false
    return arrivals().contains(dest)
}

private fun ActionDefinition.arrivals(): Set<String> =
    success.effects.mapNotNull { effect ->
        when (effect) {
            is Effect.MoveTo -> effect.locationId
            is Effect.DiscoverLocation -> effect.locationId
            else -> null
        }
    }.toSet()

private fun ActionDefinition.grantedClues(): Set<String> =
    success.effects.filterIsInstance<Effect.AddClue>().map { it.clueId }.toSet()

private fun ActionDefinition.grantedEvidence(): Set<String> =
    success.effects.filterIsInstance<Effect.AddEvidence>().map { it.evidenceId }.toSet()

private fun ActionDefinition.grantedItems(): Set<String> =
    success.effects.filterIsInstance<Effect.AddItem>().map { it.itemId }.toSet()

private fun ActionDefinition.grantedDiscoveries(): Set<String> =
    success.effects.filterIsInstance<Effect.DiscoverLocation>().map { it.locationId }.toSet()
