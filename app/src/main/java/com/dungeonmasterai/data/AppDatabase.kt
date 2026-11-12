package com.dungeonmasterai.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        SavedGameEntity::class,
        InventoryItemEntity::class,
        HistoryEventEntity::class,
        AdventureGameEntity::class,
        AdventureItemEntity::class,
        AdventureFlagEntity::class,
        AdventureDiscoveryEntity::class,
        AdventureEvidenceEntity::class,
        AdventureClueEntity::class,
        AdventureRelationshipEntity::class,
        AdventureNpcStateEntity::class,
        AdventureAttemptEntity::class,
        AdventureFiredEventEntity::class,
        AdventureDeductionEntity::class,
        AdventureHypothesisEntity::class,
        AdventureLogEntity::class,
        AdventurePendingNarrationEntity::class,
        AdventureSceneProseEntity::class,
        AdventureInteractionEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun savedGameDao(): SavedGameDao

    abstract fun adventureGameDao(): AdventureGameDao
}
