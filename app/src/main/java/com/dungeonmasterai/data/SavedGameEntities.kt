package com.dungeonmasterai.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

const val SAVED_GAME_ID: Int = 1

@Entity(tableName = "saved_game")
data class SavedGameEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val characterClass: String,
    val health: Int,
    val sceneId: String,
    val outcome: String,
)

@Entity(
    tableName = "inventory_item",
    primaryKeys = ["gameId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = SavedGameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("gameId")],
)
data class InventoryItemEntity(
    val gameId: Int,
    val position: Int,
    val itemId: String,
    val itemName: String,
)

@Entity(
    tableName = "history_event",
    primaryKeys = ["gameId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = SavedGameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("gameId")],
)
data class HistoryEventEntity(
    val gameId: Int,
    val position: Int,
    val type: String,
    val text: String?,
    val attribute: String?,
    val roll: Int?,
    val modifier: Int?,
    val total: Int?,
    val difficulty: Int?,
    val success: Boolean?,
    val previousHealth: Int?,
    val currentHealth: Int?,
    val itemId: String?,
    val reason: String?,
)
