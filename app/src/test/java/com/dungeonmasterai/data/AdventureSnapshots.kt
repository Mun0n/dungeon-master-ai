package com.dungeonmasterai.data

import com.dungeonmasterai.domain.adventure.state.AdventureLog
import com.dungeonmasterai.domain.adventure.state.AdventureState
import com.dungeonmasterai.domain.adventure.state.AttemptRecord
import com.dungeonmasterai.domain.adventure.state.AttemptStatus
import com.dungeonmasterai.domain.character.Character
import com.dungeonmasterai.domain.character.CharacterClass
import com.dungeonmasterai.domain.character.Item
import com.dungeonmasterai.domain.dice.CheckResult

internal const val DEATH_ENDING: String = "death"
internal const val MIDNIGHT_EVENT: String = "midnight_bell"

internal fun confirmedAdventure(): AdventureState {
    val scholar = Character.create("Ada", CharacterClass.SCHOLAR)
    return AdventureState(
        adventureId = "watch",
        character = scholar.copy(
            health = 6,
            inventory = listOf(
                Item(id = "coin_purse", name = "Coin Purse"),
                Item(id = "rope", name = "Rope"),
            ),
        ),
        locationId = "hall",
        minutesElapsed = 130,
        midnightFired = true,
        flags = mapOf("quiet" to false, "seen" to true),
        discoveredLocationIds = setOf("yard", "hall"),
        evidenceIds = setOf("torn_page", "dust_mark"),
        clueIds = setOf("bell_note"),
        relationships = mapOf("clerk" to 2),
        npcStates = mapOf("clerk" to "busy"),
        attempts = mapOf(
            "climb" to AttemptRecord(
                status = AttemptStatus.FAILED,
                worldStamp = 1,
                relevantFacts = "items:brass_key=false",
            ),
            "wait" to AttemptRecord(status = AttemptStatus.CLOSED, worldStamp = 2),
        ),
        firedEventIds = setOf(MIDNIGHT_EVENT, "side_event"),
        deductionIds = setOf("named_conclusion"),
        endingId = "justice",
        worldStamp = 4,
        history = listOf(
            AdventureLog.ActionResolved(
                actionId = "climb",
                success = false,
                check = CheckResult(
                    roll = 3,
                    modifier = 2,
                    total = 5,
                    difficulty = 20,
                    success = false,
                ),
            ),
            AdventureLog.ActionResolved(actionId = "wait", success = true, check = null),
            AdventureLog.ClockAdvanced(fromMinutes = 0, toMinutes = 130),
            AdventureLog.MidnightFired(eventId = MIDNIGHT_EVENT),
            AdventureLog.DeductionAccepted(deductionId = "named_conclusion"),
            AdventureLog.EndingReached(endingId = "justice"),
        ),
        narrativeSummary = "The bell rang.",
    )
}

internal fun deadAdventure(): AdventureState {
    val alive = confirmedAdventure()
    return alive.copy(
        character = alive.character.copy(health = 0),
        minutesElapsed = 15,
        midnightFired = false,
        firedEventIds = emptySet(),
        endingId = DEATH_ENDING,
        history = listOf(
            AdventureLog.ActionResolved(
                actionId = "climb",
                success = false,
                check = CheckResult(roll = 1, modifier = 2, total = 3, difficulty = 20, success = false),
            ),
            AdventureLog.EndingReached(endingId = DEATH_ENDING),
        ),
        narrativeSummary = "",
    )
}

internal fun minimalAdventure(): AdventureState = AdventureState(
    adventureId = "watch",
    character = Character.create("Ada", CharacterClass.SCHOLAR),
    locationId = "yard",
    minutesElapsed = 0,
    midnightFired = false,
    flags = emptyMap(),
    discoveredLocationIds = setOf("yard"),
    evidenceIds = emptySet(),
    clueIds = emptySet(),
    relationships = emptyMap(),
    npcStates = emptyMap(),
    attempts = emptyMap(),
    firedEventIds = emptySet(),
    deductionIds = emptySet(),
    endingId = null,
    worldStamp = 0,
    history = emptyList(),
    narrativeSummary = "",
)
