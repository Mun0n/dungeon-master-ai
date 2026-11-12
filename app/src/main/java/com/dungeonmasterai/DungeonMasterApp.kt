package com.dungeonmasterai

import android.app.Application
import android.util.Log
import androidx.room.Room
import com.dungeonmasterai.data.AdventureBackend
import com.dungeonmasterai.data.AdventureGameStore
import com.dungeonmasterai.data.AppDatabase
import com.dungeonmasterai.data.HttpAdventureBackend
import com.dungeonmasterai.data.HttpNarrator
import com.dungeonmasterai.data.MIGRATION_1_2
import com.dungeonmasterai.data.MIGRATION_2_3
import com.dungeonmasterai.data.MIGRATION_3_4
import com.dungeonmasterai.data.MIGRATION_4_5
import com.dungeonmasterai.data.MIGRATION_5_6
import com.dungeonmasterai.data.Narrator
import com.dungeonmasterai.data.RoomAdventureGameStore
import com.dungeonmasterai.data.RoomSavedGameStore
import com.dungeonmasterai.data.SavedGameStore
import com.dungeonmasterai.presentation.BackgroundMusic

class DungeonMasterApp : Application() {
    private val database: AppDatabase by lazy {
        Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            DATABASE_NAME,
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build()
    }

    val savedGameStore: SavedGameStore by lazy {
        RoomSavedGameStore(database.savedGameDao())
    }

    val adventureGameStore: AdventureGameStore by lazy {
        RoomAdventureGameStore(database.adventureGameDao())
    }

    val narrator: Narrator? by lazy {
        val url = BuildConfig.NARRATOR_BASE_URL
        if (url.isBlank()) null else HttpNarrator(url)
    }

    /** Process-scoped. [MainActivity] starts it while visible and pauses it in the background. */
    internal val backgroundMusic: BackgroundMusic by lazy { BackgroundMusic(this) }

    /**
     * Present when `adventure.backendUrl` is set. The bridge screen does not call it.
     * Release builds reject an http URL. Debug builds may use the local addresses
     * documented in backend/README.md.
     */
    val adventureBackend: AdventureBackend? by lazy {
        val url = BuildConfig.ADVENTURE_BACKEND_URL
        if (url.isBlank()) {
            null
        } else {
            try {
                HttpAdventureBackend(baseUrl = url, allowCleartext = BuildConfig.DEBUG)
            } catch (error: IllegalArgumentException) {
                Log.e(TAG, "Adventure backend URL was rejected.", error)
                null
            }
        }
    }

    private companion object {
        const val DATABASE_NAME: String = "dungeon-master.db"
        const val TAG: String = "DungeonMasterApp"
    }
}
