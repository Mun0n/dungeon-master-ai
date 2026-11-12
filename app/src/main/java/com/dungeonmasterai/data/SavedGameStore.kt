package com.dungeonmasterai.data

import com.dungeonmasterai.domain.game.GameState

/**
 * Reads and writes a snapshot the engine already produced.
 * It does not roll dice or change health, inventory, or outcome.
 */
interface SavedGameStore {
    suspend fun load(): GameState?

    suspend fun save(state: GameState)
}
