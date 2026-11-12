package com.dungeonmasterai.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

private const val SELECT_GAME = "SELECT * FROM saved_game WHERE id = $SAVED_GAME_ID"
private const val SELECT_ITEMS =
    "SELECT * FROM inventory_item WHERE gameId = $SAVED_GAME_ID ORDER BY position"
private const val SELECT_HISTORY =
    "SELECT * FROM history_event WHERE gameId = $SAVED_GAME_ID ORDER BY position"

@Dao
abstract class SavedGameDao {
    @Query(SELECT_GAME)
    abstract suspend fun loadGame(): SavedGameEntity?

    @Query(SELECT_ITEMS)
    abstract suspend fun loadItems(): List<InventoryItemEntity>

    @Query(SELECT_HISTORY)
    abstract suspend fun loadHistory(): List<HistoryEventEntity>

    @Insert
    abstract suspend fun insertGame(game: SavedGameEntity)

    @Insert
    abstract suspend fun insertItems(items: List<InventoryItemEntity>)

    @Insert
    abstract suspend fun insertHistory(events: List<HistoryEventEntity>)

    @Query("DELETE FROM history_event")
    abstract suspend fun clearHistory()

    @Query("DELETE FROM inventory_item")
    abstract suspend fun clearItems()

    @Query("DELETE FROM saved_game")
    abstract suspend fun clearGame()

    @Transaction
    open suspend fun loadRows(): SavedGameRows? {
        val game = loadGame() ?: return null
        return SavedGameRows(
            game = game,
            inventory = loadItems(),
            history = loadHistory(),
        )
    }

    @Transaction
    open suspend fun replace(rows: SavedGameRows) {
        clearHistory()
        clearItems()
        clearGame()
        insertGame(rows.game)
        if (rows.inventory.isNotEmpty()) {
            insertItems(rows.inventory)
        }
        if (rows.history.isNotEmpty()) {
            insertHistory(rows.history)
        }
    }

    @Transaction
    open suspend fun clearAll() {
        clearHistory()
        clearItems()
        clearGame()
    }
}
