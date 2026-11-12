package com.dungeonmasterai.data

import com.dungeonmasterai.domain.adventure.load.adventureJson
import com.dungeonmasterai.domain.adventure.model.AdventureRules
import com.dungeonmasterai.domain.adventure.model.Effect
import com.dungeonmasterai.domain.dice.CheckResult
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer

private val TOKEN: Regex = Regex("^[A-Za-z0-9_-]{1,64}$")
private val ATTRIBUTES: Set<String> = setOf("STRENGTH", "AGILITY", "INTELLIGENCE")
private val DIFFICULTIES: Set<Int> = setOf(8, 12, 16, 20)
private val effectList = ListSerializer(Effect.serializer())

internal fun PendingNarration.toEntity(): AdventurePendingNarrationEntity {
    val encoded = try {
        adventureJson.encodeToString(effectList, appliedEffects)
    } catch (error: SerializationException) {
        throw IllegalArgumentException("Applied effects could not be stored.", error)
    }
    return AdventurePendingNarrationEntity(
        gameId = ADVENTURE_GAME_ID,
        status = delivery.name,
        requestId = requestId,
        stateRevision = stateRevision,
        sceneContractId = sceneContractId,
        actionId = actionId,
        outcome = outcome,
        success = success,
        attribute = attribute,
        roll = check?.roll,
        modifier = check?.modifier,
        total = check?.total,
        difficulty = check?.difficulty,
        checkSuccess = check?.success,
        appliedEffectsJson = encoded,
        text = text,
    )
}

internal fun AdventurePendingNarrationEntity.toPending(): PendingNarration {
    if (gameId != ADVENTURE_GAME_ID) fail("Wrong pending slot.")
    if (!TOKEN.matches(requestId) || !TOKEN.matches(stateRevision)) fail("Pending token is invalid.")
    if (!AdventureRules.ID_PATTERN.matches(sceneContractId)) fail("Pending scene is invalid.")
    if (!AdventureRules.ID_PATTERN.matches(actionId)) fail("Pending action is invalid.")
    val delivery = when (status) {
        NarrationDelivery.AWAITING.name -> NarrationDelivery.AWAITING
        NarrationDelivery.VALIDATED.name -> NarrationDelivery.VALIDATED
        else -> fail("Pending status is invalid.")
    }
    if (outcome != "SUCCEEDED" && outcome != "FAILED") fail("Pending outcome is invalid.")
    val succeeded = outcome == "SUCCEEDED"
    if (success != succeeded) fail("Pending success flag does not match the outcome.")
    val check = toCheck()
    if (outcome == "FAILED" && (check == null || check.success)) fail("A failure needs a failed check.")
    if (outcome == "SUCCEEDED" && check != null && !check.success) fail("A success check succeeded.")
    val prose = when (delivery) {
        NarrationDelivery.AWAITING -> {
            if (text != null) fail("An awaiting narration has no stored text.")
            null
        }
        NarrationDelivery.VALIDATED -> {
            val stored = text?.trim().orEmpty()
            if (stored.isEmpty() || stored.length > AdventureRules.LOCAL_PROSE_MAX) {
                fail("Stored narration is empty or too long.")
            }
            stored
        }
    }
    val effects = try {
        adventureJson.decodeFromString(effectList, appliedEffectsJson)
    } catch (error: SerializationException) {
        fail("Stored effects could not be read.")
    } catch (error: IllegalArgumentException) {
        fail("Stored effects could not be read.")
    }
    return PendingNarration(
        delivery = delivery,
        requestId = requestId,
        stateRevision = stateRevision,
        sceneContractId = sceneContractId,
        actionId = actionId,
        outcome = outcome,
        success = success,
        attribute = attribute,
        check = check,
        appliedEffects = effects,
        text = prose,
    )
}

internal fun Map<String, String>.toSceneEntities(): List<AdventureSceneProseEntity> =
    toSortedMap().map { (contractId, prose) ->
        if (!AdventureRules.ID_PATTERN.matches(contractId)) fail("Scene contract id is invalid.")
        val text = prose.trim()
        if (text.isEmpty() || text.length > AdventureRules.SCENE_TEXT_MAX) fail("Scene prose is empty or too long.")
        AdventureSceneProseEntity(
            gameId = ADVENTURE_GAME_ID,
            sceneContractId = contractId,
            text = text,
        )
    }

internal fun List<RecentInteraction>.toInteractionEntities(): List<AdventureInteractionEntity> {
    if (size > AdventureRules.RECENT_LINES) fail("Too many recent lines.")
    return mapIndexed { position, line ->
        if (line.speaker != "player" && line.speaker != "narration") fail("Unknown speaker.")
        val text = line.text.trim()
        val cap = if (line.speaker == "player") AdventureRules.PLAYER_TEXT_MAX else AdventureRules.LOCAL_PROSE_MAX
        if (text.isEmpty() || text.length > cap) fail("A recent line is empty or too long.")
        AdventureInteractionEntity(
            gameId = ADVENTURE_GAME_ID,
            position = position,
            speaker = line.speaker,
            text = text,
        )
    }
}

private fun AdventurePendingNarrationEntity.toCheck(): CheckResult? {
    val parts = listOf(roll, modifier, total, difficulty, checkSuccess)
    if (parts.all { it == null } && attribute == null) return null
    if (attribute !in ATTRIBUTES) fail("Pending attribute is invalid.")
    val check = CheckResult(
        roll = roll ?: fail("Pending roll is missing."),
        modifier = modifier ?: fail("Pending modifier is missing."),
        total = total ?: fail("Pending total is missing."),
        difficulty = difficulty ?: fail("Pending difficulty is missing."),
        success = checkSuccess ?: fail("Pending check result is missing."),
    )
    if (check.roll !in 1..20 || check.modifier !in 1..5) fail("Pending check is out of range.")
    if (check.difficulty !in DIFFICULTIES) fail("Pending difficulty is invalid.")
    if (check.total != check.roll + check.modifier) fail("Pending total does not match the roll.")
    if (check.success != (check.total >= check.difficulty)) fail("Pending success does not match the total.")
    return check
}

private fun fail(message: String): Nothing = throw IllegalArgumentException(message)
