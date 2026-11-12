package com.dungeonmasterai.domain.adventure.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Closed effect list. The JSON names one of these types.
 * Kotlin applies it. There is no script, expression, or nested condition here.
 */
val EFFECT_TYPE_NAMES: List<String> = listOf(
    "SET_FLAG",
    "CHANGE_LIFE",
    "ADD_ITEM",
    "REMOVE_ITEM",
    "DISCOVER_LOCATION",
    "MOVE_TO",
    "SET_RELATIONSHIP",
    "ADD_EVIDENCE",
    "ADD_CLUE",
    "SET_NPC_STATE",
    "RECORD_ATTEMPT",
    "TRIGGER_EVENT",
    "SET_ENDING",
)

@Serializable
sealed interface Effect {
    @Serializable
    @SerialName("SET_FLAG")
    data class SetFlag(
        val key: String,
        val value: Boolean,
    ) : Effect

    @Serializable
    @SerialName("CHANGE_LIFE")
    data class ChangeLife(
        val amount: Int,
    ) : Effect

    @Serializable
    @SerialName("ADD_ITEM")
    data class AddItem(
        val itemId: String,
    ) : Effect

    @Serializable
    @SerialName("REMOVE_ITEM")
    data class RemoveItem(
        val itemId: String,
    ) : Effect

    @Serializable
    @SerialName("DISCOVER_LOCATION")
    data class DiscoverLocation(
        val locationId: String,
    ) : Effect

    @Serializable
    @SerialName("MOVE_TO")
    data class MoveTo(
        val locationId: String,
    ) : Effect

    @Serializable
    @SerialName("SET_RELATIONSHIP")
    data class SetRelationship(
        val npcId: String,
        val value: Int,
    ) : Effect

    @Serializable
    @SerialName("ADD_EVIDENCE")
    data class AddEvidence(
        val evidenceId: String,
    ) : Effect

    @Serializable
    @SerialName("ADD_CLUE")
    data class AddClue(
        val clueId: String,
    ) : Effect

    @Serializable
    @SerialName("SET_NPC_STATE")
    data class SetNpcState(
        val npcId: String,
        val state: String,
    ) : Effect

    @Serializable
    @SerialName("RECORD_ATTEMPT")
    data class RecordAttempt(
        val actionId: String,
    ) : Effect

    @Serializable
    @SerialName("TRIGGER_EVENT")
    data class TriggerEvent(
        val eventId: String,
    ) : Effect

    @Serializable
    @SerialName("SET_ENDING")
    data class SetEnding(
        val endingId: String,
    ) : Effect
}
