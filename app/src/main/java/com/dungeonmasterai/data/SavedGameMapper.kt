package com.dungeonmasterai.data

import com.dungeonmasterai.domain.character.Attribute
import com.dungeonmasterai.domain.character.Attributes
import com.dungeonmasterai.domain.character.Character
import com.dungeonmasterai.domain.character.CharacterClass
import com.dungeonmasterai.domain.character.Item
import com.dungeonmasterai.domain.game.GameEvent
import com.dungeonmasterai.domain.game.GameState
import com.dungeonmasterai.domain.game.Outcome
import com.dungeonmasterai.domain.game.RejectionReason
import com.dungeonmasterai.domain.scenario.GuardiansBridge

data class SavedGameRows(
    val game: SavedGameEntity,
    val inventory: List<InventoryItemEntity>,
    val history: List<HistoryEventEntity>,
)

internal fun GameState.toRows(): SavedGameRows = SavedGameRows(
    game = SavedGameEntity(
        id = SAVED_GAME_ID,
        name = character.name,
        characterClass = character.characterClass.name,
        health = character.health,
        sceneId = sceneId,
        outcome = outcome.name,
    ),
    inventory = character.inventory.mapIndexed { position, item ->
        InventoryItemEntity(
            gameId = SAVED_GAME_ID,
            position = position,
            itemId = item.id,
            itemName = item.name,
        )
    },
    history = history.mapIndexed { position, event -> event.toEntity(position) },
)

internal fun SavedGameRows.toGameState(): GameState {
    if (game.sceneId != GuardiansBridge.SCENE_ID) {
        throw IllegalArgumentException("Wrong scene: ${game.sceneId}.")
    }
    val events = history.sortedBy { it.position }.map { it.toEvent() }
    if (events.isEmpty()) {
        throw IllegalArgumentException("History must not be empty.")
    }
    val characterClass = CharacterClass.valueOf(game.characterClass)
    return GameState(
        character = Character(
            name = game.name,
            characterClass = characterClass,
            attributes = Attributes.of(characterClass),
            health = game.health,
            inventory = inventory.sortedBy { it.position }.map { item ->
                Item(id = item.itemId, name = item.itemName)
            },
        ),
        sceneId = game.sceneId,
        outcome = Outcome.valueOf(game.outcome),
        history = events,
    )
}

private fun GameEvent.toEntity(position: Int): HistoryEventEntity = when (this) {
    is GameEvent.Narration -> eventEntity(position, type = TYPE_NARRATION, text = text)
    is GameEvent.CheckResolved -> eventEntity(
        position,
        type = TYPE_CHECK,
        attribute = attribute.name,
        roll = roll,
        modifier = modifier,
        total = total,
        difficulty = difficulty,
        success = success,
    )
    is GameEvent.HealthChanged -> eventEntity(
        position,
        type = TYPE_HEALTH,
        previousHealth = previous,
        currentHealth = current,
    )
    is GameEvent.ItemRemoved -> eventEntity(position, type = TYPE_ITEM, itemId = itemId)
    is GameEvent.ActionRejected -> eventEntity(position, type = TYPE_REJECTED, reason = reason.name)
}

private fun HistoryEventEntity.toEvent(): GameEvent = when (type) {
    TYPE_NARRATION -> GameEvent.Narration(text = required(text, "narration text"))
    TYPE_CHECK -> GameEvent.CheckResolved(
        attribute = Attribute.valueOf(required(attribute, "attribute")),
        roll = required(roll, "roll"),
        modifier = required(modifier, "modifier"),
        total = required(total, "total"),
        difficulty = required(difficulty, "difficulty"),
        success = required(success, "success"),
    )
    TYPE_HEALTH -> GameEvent.HealthChanged(
        previous = required(previousHealth, "previous health"),
        current = required(currentHealth, "current health"),
    )
    TYPE_ITEM -> GameEvent.ItemRemoved(itemId = required(itemId, "item id"))
    TYPE_REJECTED -> GameEvent.ActionRejected(
        reason = RejectionReason.valueOf(required(reason, "rejection reason")),
    )
    else -> throw IllegalArgumentException("Unknown event type: $type.")
}

private fun eventEntity(
    position: Int,
    type: String,
    text: String? = null,
    attribute: String? = null,
    roll: Int? = null,
    modifier: Int? = null,
    total: Int? = null,
    difficulty: Int? = null,
    success: Boolean? = null,
    previousHealth: Int? = null,
    currentHealth: Int? = null,
    itemId: String? = null,
    reason: String? = null,
): HistoryEventEntity = HistoryEventEntity(
    gameId = SAVED_GAME_ID,
    position = position,
    type = type,
    text = text,
    attribute = attribute,
    roll = roll,
    modifier = modifier,
    total = total,
    difficulty = difficulty,
    success = success,
    previousHealth = previousHealth,
    currentHealth = currentHealth,
    itemId = itemId,
    reason = reason,
)

private fun <T> required(value: T?, field: String): T =
    value ?: throw IllegalArgumentException("Missing $field.")

private const val TYPE_NARRATION: String = "NARRATION"
private const val TYPE_CHECK: String = "CHECK"
private const val TYPE_HEALTH: String = "HEALTH"
private const val TYPE_ITEM: String = "ITEM"
private const val TYPE_REJECTED: String = "REJECTED"
