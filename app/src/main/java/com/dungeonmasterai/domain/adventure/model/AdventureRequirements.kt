package com.dungeonmasterai.domain.adventure.model

import kotlinx.serialization.Serializable

@Serializable
data class RelationshipAtLeast(
    val npcId: String,
    val atLeast: Int,
)

@Serializable
data class NpcStateIs(
    val npcId: String,
    val state: String,
)

/**
 * A requirement holds only when every listed constraint holds.
 * An empty requirement always holds. Omitted JSON fields mean empty.
 */
@Serializable
data class Requirement(
    val flagIds: List<String> = emptyList(),
    val absentFlagIds: List<String> = emptyList(),
    val itemIds: List<String> = emptyList(),
    val evidenceIds: List<String> = emptyList(),
    val clueIds: List<String> = emptyList(),
    val absentClueIds: List<String> = emptyList(),
    val relationships: List<RelationshipAtLeast> = emptyList(),
    val npcStates: List<NpcStateIs> = emptyList(),
    val discoveredLocationIds: List<String> = emptyList(),
    val deductionIds: List<String> = emptyList(),
    val atLeastMinutes: Int? = null,
    val beforeMidnight: Boolean? = null,
    val afterMidnight: Boolean? = null,
) {
    fun isEmpty(): Boolean = flagIds.isEmpty() &&
        absentFlagIds.isEmpty() &&
        itemIds.isEmpty() &&
        evidenceIds.isEmpty() &&
        clueIds.isEmpty() &&
        absentClueIds.isEmpty() &&
        relationships.isEmpty() &&
        npcStates.isEmpty() &&
        discoveredLocationIds.isEmpty() &&
        deductionIds.isEmpty() &&
        atLeastMinutes == null &&
        beforeMidnight == null &&
        afterMidnight == null
}
