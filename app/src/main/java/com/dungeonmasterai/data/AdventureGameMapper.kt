package com.dungeonmasterai.data

import com.dungeonmasterai.domain.adventure.state.AdventureLog
import com.dungeonmasterai.domain.adventure.state.AdventureState
import com.dungeonmasterai.domain.adventure.state.AttemptRecord
import com.dungeonmasterai.domain.adventure.state.AttemptStatus
import com.dungeonmasterai.domain.adventure.state.Hypothesis
import com.dungeonmasterai.domain.character.Attributes
import com.dungeonmasterai.domain.character.Character
import com.dungeonmasterai.domain.character.CharacterClass
import com.dungeonmasterai.domain.character.Item
import com.dungeonmasterai.domain.character.Presentation
import com.dungeonmasterai.domain.dice.CheckResult

data class AdventureRows(
    val game: AdventureGameEntity,
    val items: List<AdventureItemEntity>,
    val flags: List<AdventureFlagEntity>,
    val discoveries: List<AdventureDiscoveryEntity>,
    val evidence: List<AdventureEvidenceEntity>,
    val clues: List<AdventureClueEntity>,
    val relationships: List<AdventureRelationshipEntity>,
    val npcStates: List<AdventureNpcStateEntity>,
    val attempts: List<AdventureAttemptEntity>,
    val firedEvents: List<AdventureFiredEventEntity>,
    val deductions: List<AdventureDeductionEntity>,
    val hypotheses: List<AdventureHypothesisEntity> = emptyList(),
    val history: List<AdventureLogEntity>,
    val pending: AdventurePendingNarrationEntity? = null,
    val sceneProse: List<AdventureSceneProseEntity> = emptyList(),
    val interactions: List<AdventureInteractionEntity> = emptyList(),
)

internal fun AdventureState.toRows(
    deathEndingId: String,
    midnightEventId: String,
    revision: Int = 0,
): AdventureRows {
    require(deathEndingId.isNotBlank()) { "Death ending id must not be blank." }
    require(midnightEventId.isNotBlank()) { "Midnight event id must not be blank." }
    require(revision >= 0) { "Revision must be >= 0." }
    val rows = AdventureRows(
        game = AdventureGameEntity(
            id = ADVENTURE_GAME_ID,
            schemaVersion = ADVENTURE_SNAPSHOT_VERSION,
            adventureId = adventureId,
            deathEndingId = deathEndingId,
            midnightEventId = midnightEventId,
            name = character.name,
            characterClass = character.characterClass.name,
            health = character.health,
            strength = character.attributes.strength,
            agility = character.attributes.agility,
            intelligence = character.attributes.intelligence,
            locationId = locationId,
            minutesElapsed = minutesElapsed,
            midnightFired = midnightFired,
            endingId = endingId,
            worldStamp = worldStamp,
            narrativeSummary = narrativeSummary,
            stateRevision = revision,
            presentation = character.presentation.name,
        ),
        items = character.inventory.mapIndexed { position, item ->
            AdventureItemEntity(
                gameId = ADVENTURE_GAME_ID,
                position = position,
                itemId = item.id,
                itemName = item.name,
            )
        },
        flags = flags.toSortedMap().map { (flagId, value) ->
            AdventureFlagEntity(gameId = ADVENTURE_GAME_ID, flagId = flagId, value = value)
        },
        discoveries = discoveredLocationIds.sorted().map { locationId ->
            AdventureDiscoveryEntity(gameId = ADVENTURE_GAME_ID, locationId = locationId)
        },
        evidence = evidenceIds.sorted().map { evidenceId ->
            AdventureEvidenceEntity(gameId = ADVENTURE_GAME_ID, evidenceId = evidenceId)
        },
        clues = clueIds.sorted().map { clueId ->
            AdventureClueEntity(gameId = ADVENTURE_GAME_ID, clueId = clueId)
        },
        relationships = relationships.toSortedMap().map { (npcId, value) ->
            AdventureRelationshipEntity(gameId = ADVENTURE_GAME_ID, npcId = npcId, value = value)
        },
        npcStates = npcStates.toSortedMap().map { (npcId, state) ->
            AdventureNpcStateEntity(gameId = ADVENTURE_GAME_ID, npcId = npcId, state = state)
        },
        attempts = attempts.toSortedMap().map { (actionId, record) ->
            AdventureAttemptEntity(
                gameId = ADVENTURE_GAME_ID,
                actionId = actionId,
                status = record.status.name,
                worldStamp = record.worldStamp,
                relevantFacts = record.relevantFacts,
            )
        },
        firedEvents = firedEventIds.sorted().map { eventId ->
            AdventureFiredEventEntity(gameId = ADVENTURE_GAME_ID, eventId = eventId)
        },
        deductions = deductionIds.sorted().map { deductionId ->
            AdventureDeductionEntity(gameId = ADVENTURE_GAME_ID, deductionId = deductionId)
        },
        hypotheses = hypotheses.mapIndexed { position, hypothesis ->
            AdventureHypothesisEntity(
                gameId = ADVENTURE_GAME_ID,
                position = position,
                text = hypothesis.text,
            )
        },
        history = history.mapIndexed { position, event -> event.toEntity(position) },
    )
    // A snapshot this store cannot load must fail before the previous commit is replaced.
    rows.toAdventureState()
    return rows
}

internal fun AdventureRows.toAdventureState(): AdventureState {
    if (game.schemaVersion != ADVENTURE_SNAPSHOT_VERSION) {
        throw IllegalArgumentException(
            "Unsupported adventure snapshot version: ${game.schemaVersion}.",
        )
    }
    if (game.id != ADVENTURE_GAME_ID) {
        throw IllegalArgumentException("Wrong adventure slot: ${game.id}.")
    }
    requireField(game.deathEndingId.isNotBlank()) { "Death ending id must not be blank." }
    requireField(game.midnightEventId.isNotBlank()) { "Midnight event id must not be blank." }
    requireField(game.endingId?.isNotBlank() != false) { "Ending id must not be blank." }
    val firedEventIds = firedEvents.map { it.eventId }.toSet()
    if (game.midnightFired != (game.midnightEventId in firedEventIds)) {
        throw IllegalArgumentException("Midnight flag does not match the fired midnight event.")
    }
    requirePositions(items.map { it.position }, "Inventory")
    requirePositions(history.map { it.position }, "History")
    requirePositions(hypotheses.map { it.position }, "Hypotheses")
    val characterClass = CharacterClass.valueOf(game.characterClass)
    val state = AdventureState(
        adventureId = game.adventureId,
        character = Character(
            name = game.name,
            characterClass = characterClass,
            attributes = Attributes(
                strength = game.strength,
                agility = game.agility,
                intelligence = game.intelligence,
            ),
            health = game.health,
            inventory = items.sortedBy { it.position }.map { item ->
                Item(id = item.itemId, name = item.itemName)
            },
            presentation = Presentation.valueOf(game.presentation),
        ),
        locationId = game.locationId,
        minutesElapsed = game.minutesElapsed,
        midnightFired = game.midnightFired,
        flags = flags.associate { it.flagId to it.value },
        discoveredLocationIds = discoveries.map { it.locationId }.toSet(),
        evidenceIds = evidence.map { it.evidenceId }.toSet(),
        clueIds = clues.map { it.clueId }.toSet(),
        relationships = relationships.associate { it.npcId to it.value },
        npcStates = npcStates.associate { it.npcId to it.state },
        attempts = attempts.associate { attempt ->
            requireField(attempt.worldStamp >= 0) { "Attempt stamp must be >= 0." }
            attempt.actionId to AttemptRecord(
                status = AttemptStatus.valueOf(attempt.status),
                worldStamp = attempt.worldStamp,
                relevantFacts = attempt.relevantFacts,
            )
        },
        firedEventIds = firedEventIds,
        deductionIds = deductions.map { it.deductionId }.toSet(),
        hypotheses = hypotheses.sortedBy { it.position }.map { Hypothesis(it.text) },
        endingId = game.endingId,
        worldStamp = game.worldStamp,
        history = history.sortedBy { it.position }.map { it.toLog() },
        narrativeSummary = game.narrativeSummary,
    )
    if (state.endingId == game.deathEndingId && state.character.health != 0) {
        throw IllegalArgumentException("A death ending restores only at health 0.")
    }
    if (state.character.health == 0 && state.endingId != game.deathEndingId) {
        throw IllegalArgumentException("Health 0 restores only with the death ending.")
    }
    return state
}

private fun AdventureLog.toEntity(position: Int): AdventureLogEntity = when (this) {
    is AdventureLog.ActionResolved -> logEntity(
        position,
        type = TYPE_ACTION,
        actionId = actionId,
        success = success,
        roll = check?.roll,
        modifier = check?.modifier,
        total = check?.total,
        difficulty = check?.difficulty,
        checkSuccess = check?.success,
    )
    is AdventureLog.ClockAdvanced -> logEntity(
        position,
        type = TYPE_CLOCK,
        fromMinutes = fromMinutes,
        toMinutes = toMinutes,
    )
    is AdventureLog.MidnightFired -> logEntity(position, type = TYPE_MIDNIGHT, eventId = eventId)
    is AdventureLog.EndingReached -> logEntity(position, type = TYPE_ENDING, endingId = endingId)
    is AdventureLog.DeductionAccepted -> logEntity(
        position,
        type = TYPE_DEDUCTION,
        deductionId = deductionId,
    )
}

private fun AdventureLogEntity.toLog(): AdventureLog = when (type) {
    TYPE_ACTION -> AdventureLog.ActionResolved(
        actionId = required(actionId, "action id"),
        success = required(success, "success"),
        check = toCheck(),
    )
    TYPE_CLOCK -> AdventureLog.ClockAdvanced(
        fromMinutes = required(fromMinutes, "from minutes"),
        toMinutes = required(toMinutes, "to minutes"),
    )
    TYPE_MIDNIGHT -> AdventureLog.MidnightFired(eventId = required(eventId, "event id"))
    TYPE_ENDING -> AdventureLog.EndingReached(endingId = required(endingId, "ending id"))
    TYPE_DEDUCTION -> AdventureLog.DeductionAccepted(
        deductionId = required(deductionId, "deduction id"),
    )
    else -> throw IllegalArgumentException("Unknown adventure log type: $type.")
}

private fun AdventureLogEntity.toCheck(): CheckResult? {
    val present = listOf(roll, modifier, total, difficulty, checkSuccess)
    if (present.all { it == null }) return null
    return CheckResult(
        roll = required(roll, "roll"),
        modifier = required(modifier, "modifier"),
        total = required(total, "total"),
        difficulty = required(difficulty, "difficulty"),
        success = required(checkSuccess, "check success"),
    )
}

private fun logEntity(
    position: Int,
    type: String,
    actionId: String? = null,
    success: Boolean? = null,
    roll: Int? = null,
    modifier: Int? = null,
    total: Int? = null,
    difficulty: Int? = null,
    checkSuccess: Boolean? = null,
    fromMinutes: Int? = null,
    toMinutes: Int? = null,
    eventId: String? = null,
    endingId: String? = null,
    deductionId: String? = null,
): AdventureLogEntity = AdventureLogEntity(
    gameId = ADVENTURE_GAME_ID,
    position = position,
    type = type,
    actionId = actionId,
    success = success,
    roll = roll,
    modifier = modifier,
    total = total,
    difficulty = difficulty,
    checkSuccess = checkSuccess,
    fromMinutes = fromMinutes,
    toMinutes = toMinutes,
    eventId = eventId,
    endingId = endingId,
    deductionId = deductionId,
)

private fun requirePositions(positions: List<Int>, label: String) {
    if (positions.sorted() != (0 until positions.size).toList()) {
        throw IllegalArgumentException("$label positions must be contiguous.")
    }
}

private fun requireField(holds: Boolean, message: () -> String) {
    if (!holds) throw IllegalArgumentException(message())
}

private fun <T> required(value: T?, field: String): T =
    value ?: throw IllegalArgumentException("Missing $field.")

private const val TYPE_ACTION: String = "ACTION"
private const val TYPE_CLOCK: String = "CLOCK"
private const val TYPE_MIDNIGHT: String = "MIDNIGHT"
private const val TYPE_ENDING: String = "ENDING"
private const val TYPE_DEDUCTION: String = "DEDUCTION"
