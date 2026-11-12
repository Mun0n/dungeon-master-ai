package com.dungeonmasterai.data

import com.dungeonmasterai.domain.game.GameState

class MemorySavedGameStore : SavedGameStore {
    var saved: GameState? = null
        private set
    var saveCount: Int = 0
        private set

    override suspend fun load(): GameState? = saved

    override suspend fun save(state: GameState) {
        saved = state
        saveCount += 1
    }
}
