package com.dungeonmasterai.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

const val ADVENTURE_GAME_ID: Int = 1

/** Snapshot shape stored in the adventure tables. Independent of the Room version. */
const val ADVENTURE_SNAPSHOT_VERSION: Int = 1

@Entity(tableName = "adventure_game")
data class AdventureGameEntity(
    @PrimaryKey val id: Int,
    val schemaVersion: Int,
    val adventureId: String,
    val deathEndingId: String,
    val midnightEventId: String,
    val name: String,
    val characterClass: String,
    val health: Int,
    val strength: Int,
    val agility: Int,
    val intelligence: Int,
    val locationId: String,
    val minutesElapsed: Int,
    val midnightFired: Boolean,
    val endingId: String?,
    val worldStamp: Int,
    val narrativeSummary: String,
    @ColumnInfo(defaultValue = "0")
    val stateRevision: Int,
    @ColumnInfo(defaultValue = "'MASCULINE'")
    val presentation: String,
)

@Entity(
    tableName = "adventure_item",
    primaryKeys = ["gameId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = AdventureGameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("gameId")],
)
data class AdventureItemEntity(
    val gameId: Int,
    val position: Int,
    val itemId: String,
    val itemName: String,
)

@Entity(
    tableName = "adventure_flag",
    primaryKeys = ["gameId", "flagId"],
    foreignKeys = [
        ForeignKey(
            entity = AdventureGameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("gameId")],
)
data class AdventureFlagEntity(
    val gameId: Int,
    val flagId: String,
    val value: Boolean,
)

@Entity(
    tableName = "adventure_discovery",
    primaryKeys = ["gameId", "locationId"],
    foreignKeys = [
        ForeignKey(
            entity = AdventureGameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("gameId")],
)
data class AdventureDiscoveryEntity(
    val gameId: Int,
    val locationId: String,
)

@Entity(
    tableName = "adventure_evidence",
    primaryKeys = ["gameId", "evidenceId"],
    foreignKeys = [
        ForeignKey(
            entity = AdventureGameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("gameId")],
)
data class AdventureEvidenceEntity(
    val gameId: Int,
    val evidenceId: String,
)

@Entity(
    tableName = "adventure_clue",
    primaryKeys = ["gameId", "clueId"],
    foreignKeys = [
        ForeignKey(
            entity = AdventureGameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("gameId")],
)
data class AdventureClueEntity(
    val gameId: Int,
    val clueId: String,
)

@Entity(
    tableName = "adventure_relationship",
    primaryKeys = ["gameId", "npcId"],
    foreignKeys = [
        ForeignKey(
            entity = AdventureGameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("gameId")],
)
data class AdventureRelationshipEntity(
    val gameId: Int,
    val npcId: String,
    val value: Int,
)

@Entity(
    tableName = "adventure_npc_state",
    primaryKeys = ["gameId", "npcId"],
    foreignKeys = [
        ForeignKey(
            entity = AdventureGameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("gameId")],
)
data class AdventureNpcStateEntity(
    val gameId: Int,
    val npcId: String,
    val state: String,
)

@Entity(
    tableName = "adventure_attempt",
    primaryKeys = ["gameId", "actionId"],
    foreignKeys = [
        ForeignKey(
            entity = AdventureGameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("gameId")],
)
data class AdventureAttemptEntity(
    val gameId: Int,
    val actionId: String,
    val status: String,
    val worldStamp: Int,
    @ColumnInfo(defaultValue = "''")
    val relevantFacts: String = "",
)

@Entity(
    tableName = "adventure_fired_event",
    primaryKeys = ["gameId", "eventId"],
    foreignKeys = [
        ForeignKey(
            entity = AdventureGameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("gameId")],
)
data class AdventureFiredEventEntity(
    val gameId: Int,
    val eventId: String,
)

@Entity(
    tableName = "adventure_deduction",
    primaryKeys = ["gameId", "deductionId"],
    foreignKeys = [
        ForeignKey(
            entity = AdventureGameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("gameId")],
)
data class AdventureDeductionEntity(
    val gameId: Int,
    val deductionId: String,
)

@Entity(
    tableName = "adventure_hypothesis",
    primaryKeys = ["gameId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = AdventureGameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("gameId")],
)
data class AdventureHypothesisEntity(
    val gameId: Int,
    val position: Int,
    val text: String,
)

@Entity(
    tableName = "adventure_log",
    primaryKeys = ["gameId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = AdventureGameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("gameId")],
)
data class AdventureLogEntity(
    val gameId: Int,
    val position: Int,
    val type: String,
    val actionId: String?,
    val success: Boolean?,
    val roll: Int?,
    val modifier: Int?,
    val total: Int?,
    val difficulty: Int?,
    val checkSuccess: Boolean?,
    val fromMinutes: Int?,
    val toMinutes: Int?,
    val eventId: String?,
    val endingId: String?,
    val deductionId: String?,
)

@Entity(
    tableName = "adventure_pending_narration",
    foreignKeys = [
        ForeignKey(
            entity = AdventureGameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class AdventurePendingNarrationEntity(
    @PrimaryKey val gameId: Int,
    val status: String,
    val requestId: String,
    val stateRevision: String,
    val sceneContractId: String,
    val actionId: String,
    val outcome: String,
    val success: Boolean,
    val attribute: String?,
    val roll: Int?,
    val modifier: Int?,
    val total: Int?,
    val difficulty: Int?,
    val checkSuccess: Boolean?,
    val appliedEffectsJson: String,
    val text: String?,
)

@Entity(
    tableName = "adventure_scene_prose",
    primaryKeys = ["gameId", "sceneContractId"],
    foreignKeys = [
        ForeignKey(
            entity = AdventureGameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("gameId")],
)
data class AdventureSceneProseEntity(
    val gameId: Int,
    val sceneContractId: String,
    val text: String,
)

@Entity(
    tableName = "adventure_interaction",
    primaryKeys = ["gameId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = AdventureGameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("gameId")],
)
data class AdventureInteractionEntity(
    val gameId: Int,
    val position: Int,
    val speaker: String,
    val text: String,
)
