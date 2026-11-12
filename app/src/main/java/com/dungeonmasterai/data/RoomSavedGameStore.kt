package com.dungeonmasterai.data

import com.dungeonmasterai.domain.game.GameState

class RoomSavedGameStore(
    private val dao: SavedGameDao,
) : SavedGameStore {
    override suspend fun load(): GameState? {
        val rows = dao.loadRows() ?: return null
        return try {
            rows.toGameState()
        } catch (error: IllegalArgumentException) {
            dao.clearAll()
            null
        }
    }

    override suspend fun save(state: GameState) {
        dao.replace(state.toRows())
    }
}
