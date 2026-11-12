package com.dungeonmasterai.data

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.execSQL

/**
 * Creates the adventure tables.
 * Version 1 tables for The Guardian's Bridge are not altered or deleted.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        ADVENTURE_TABLE_STATEMENTS.forEach { db.execSQL(it) }
    }

    override fun migrate(connection: SQLiteConnection) {
        ADVENTURE_TABLE_STATEMENTS.forEach { connection.execSQL(it) }
    }
}

internal val ADVENTURE_TABLE_STATEMENTS: List<String> = listOf(
    "CREATE TABLE IF NOT EXISTS `adventure_game` (`id` INTEGER NOT NULL, `schemaVersion` INTEGER NOT NULL, `adventureId` TEXT NOT NULL, `deathEndingId` TEXT NOT NULL, `midnightEventId` TEXT NOT NULL, `name` TEXT NOT NULL, `characterClass` TEXT NOT NULL, `health` INTEGER NOT NULL, `strength` INTEGER NOT NULL, `agility` INTEGER NOT NULL, `intelligence` INTEGER NOT NULL, `locationId` TEXT NOT NULL, `minutesElapsed` INTEGER NOT NULL, `midnightFired` INTEGER NOT NULL, `endingId` TEXT, `worldStamp` INTEGER NOT NULL, `narrativeSummary` TEXT NOT NULL, PRIMARY KEY(`id`))",
    "CREATE TABLE IF NOT EXISTS `adventure_item` (`gameId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `itemId` TEXT NOT NULL, `itemName` TEXT NOT NULL, PRIMARY KEY(`gameId`, `position`), FOREIGN KEY(`gameId`) REFERENCES `adventure_game`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_adventure_item_gameId` ON `adventure_item` (`gameId`)",
    "CREATE TABLE IF NOT EXISTS `adventure_flag` (`gameId` INTEGER NOT NULL, `flagId` TEXT NOT NULL, `value` INTEGER NOT NULL, PRIMARY KEY(`gameId`, `flagId`), FOREIGN KEY(`gameId`) REFERENCES `adventure_game`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_adventure_flag_gameId` ON `adventure_flag` (`gameId`)",
    "CREATE TABLE IF NOT EXISTS `adventure_discovery` (`gameId` INTEGER NOT NULL, `locationId` TEXT NOT NULL, PRIMARY KEY(`gameId`, `locationId`), FOREIGN KEY(`gameId`) REFERENCES `adventure_game`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_adventure_discovery_gameId` ON `adventure_discovery` (`gameId`)",
    "CREATE TABLE IF NOT EXISTS `adventure_evidence` (`gameId` INTEGER NOT NULL, `evidenceId` TEXT NOT NULL, PRIMARY KEY(`gameId`, `evidenceId`), FOREIGN KEY(`gameId`) REFERENCES `adventure_game`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_adventure_evidence_gameId` ON `adventure_evidence` (`gameId`)",
    "CREATE TABLE IF NOT EXISTS `adventure_clue` (`gameId` INTEGER NOT NULL, `clueId` TEXT NOT NULL, PRIMARY KEY(`gameId`, `clueId`), FOREIGN KEY(`gameId`) REFERENCES `adventure_game`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_adventure_clue_gameId` ON `adventure_clue` (`gameId`)",
    "CREATE TABLE IF NOT EXISTS `adventure_relationship` (`gameId` INTEGER NOT NULL, `npcId` TEXT NOT NULL, `value` INTEGER NOT NULL, PRIMARY KEY(`gameId`, `npcId`), FOREIGN KEY(`gameId`) REFERENCES `adventure_game`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_adventure_relationship_gameId` ON `adventure_relationship` (`gameId`)",
    "CREATE TABLE IF NOT EXISTS `adventure_npc_state` (`gameId` INTEGER NOT NULL, `npcId` TEXT NOT NULL, `state` TEXT NOT NULL, PRIMARY KEY(`gameId`, `npcId`), FOREIGN KEY(`gameId`) REFERENCES `adventure_game`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_adventure_npc_state_gameId` ON `adventure_npc_state` (`gameId`)",
    "CREATE TABLE IF NOT EXISTS `adventure_attempt` (`gameId` INTEGER NOT NULL, `actionId` TEXT NOT NULL, `status` TEXT NOT NULL, `worldStamp` INTEGER NOT NULL, PRIMARY KEY(`gameId`, `actionId`), FOREIGN KEY(`gameId`) REFERENCES `adventure_game`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_adventure_attempt_gameId` ON `adventure_attempt` (`gameId`)",
    "CREATE TABLE IF NOT EXISTS `adventure_fired_event` (`gameId` INTEGER NOT NULL, `eventId` TEXT NOT NULL, PRIMARY KEY(`gameId`, `eventId`), FOREIGN KEY(`gameId`) REFERENCES `adventure_game`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_adventure_fired_event_gameId` ON `adventure_fired_event` (`gameId`)",
    "CREATE TABLE IF NOT EXISTS `adventure_deduction` (`gameId` INTEGER NOT NULL, `deductionId` TEXT NOT NULL, PRIMARY KEY(`gameId`, `deductionId`), FOREIGN KEY(`gameId`) REFERENCES `adventure_game`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_adventure_deduction_gameId` ON `adventure_deduction` (`gameId`)",
    "CREATE TABLE IF NOT EXISTS `adventure_log` (`gameId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `type` TEXT NOT NULL, `actionId` TEXT, `success` INTEGER, `roll` INTEGER, `modifier` INTEGER, `total` INTEGER, `difficulty` INTEGER, `checkSuccess` INTEGER, `fromMinutes` INTEGER, `toMinutes` INTEGER, `eventId` TEXT, `endingId` TEXT, `deductionId` TEXT, PRIMARY KEY(`gameId`, `position`), FOREIGN KEY(`gameId`) REFERENCES `adventure_game`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_adventure_log_gameId` ON `adventure_log` (`gameId`)",
)

/**
 * Adds the revision and the narration tables.
 * Bridge tables are not altered. Existing adventure rows keep revision 0.
 */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        ADVENTURE_V3_STATEMENTS.forEach { db.execSQL(it) }
    }

    override fun migrate(connection: SQLiteConnection) {
        ADVENTURE_V3_STATEMENTS.forEach { connection.execSQL(it) }
    }
}

internal val ADVENTURE_V3_STATEMENTS: List<String> = listOf(
    "ALTER TABLE `adventure_game` ADD COLUMN `stateRevision` INTEGER NOT NULL DEFAULT 0",
    "CREATE TABLE IF NOT EXISTS `adventure_pending_narration` (`gameId` INTEGER NOT NULL, `status` TEXT NOT NULL, `requestId` TEXT NOT NULL, `stateRevision` TEXT NOT NULL, `sceneContractId` TEXT NOT NULL, `actionId` TEXT NOT NULL, `outcome` TEXT NOT NULL, `success` INTEGER NOT NULL, `attribute` TEXT, `roll` INTEGER, `modifier` INTEGER, `total` INTEGER, `difficulty` INTEGER, `checkSuccess` INTEGER, `appliedEffectsJson` TEXT NOT NULL, `text` TEXT, PRIMARY KEY(`gameId`), FOREIGN KEY(`gameId`) REFERENCES `adventure_game`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE TABLE IF NOT EXISTS `adventure_scene_prose` (`gameId` INTEGER NOT NULL, `sceneContractId` TEXT NOT NULL, `text` TEXT NOT NULL, PRIMARY KEY(`gameId`, `sceneContractId`), FOREIGN KEY(`gameId`) REFERENCES `adventure_game`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_adventure_scene_prose_gameId` ON `adventure_scene_prose` (`gameId`)",
    "CREATE TABLE IF NOT EXISTS `adventure_interaction` (`gameId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `speaker` TEXT NOT NULL, `text` TEXT NOT NULL, PRIMARY KEY(`gameId`, `position`), FOREIGN KEY(`gameId`) REFERENCES `adventure_game`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_adventure_interaction_gameId` ON `adventure_interaction` (`gameId`)",
)

/**
 * Stores the retry projection on each attempt.
 * Bridge tables are not altered. An old failure keeps an empty projection and stays closed.
 */
val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        ADVENTURE_V4_STATEMENTS.forEach { db.execSQL(it) }
    }

    override fun migrate(connection: SQLiteConnection) {
        ADVENTURE_V4_STATEMENTS.forEach { connection.execSQL(it) }
    }
}

internal val ADVENTURE_V4_STATEMENTS: List<String> = listOf(
    "ALTER TABLE `adventure_attempt` ADD COLUMN `relevantFacts` TEXT NOT NULL DEFAULT ''",
)

/**
 * Stores the character presentation.
 * Bridge tables are not altered. A save without this column restores as masculine.
 */
val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        ADVENTURE_V5_STATEMENTS.forEach { db.execSQL(it) }
    }

    override fun migrate(connection: SQLiteConnection) {
        ADVENTURE_V5_STATEMENTS.forEach { connection.execSQL(it) }
    }
}

internal val ADVENTURE_V5_STATEMENTS: List<String> = listOf(
    "ALTER TABLE `adventure_game` ADD COLUMN `presentation` TEXT NOT NULL DEFAULT 'MASCULINE'",
)

/**
 * Stores player hypotheses beside the confirmed adventure.
 * Bridge tables are not altered. A save from version 5 restores with an empty list.
 */
val MIGRATION_5_6: Migration = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        ADVENTURE_V6_STATEMENTS.forEach { db.execSQL(it) }
    }

    override fun migrate(connection: SQLiteConnection) {
        ADVENTURE_V6_STATEMENTS.forEach { connection.execSQL(it) }
    }
}

internal val ADVENTURE_V6_STATEMENTS: List<String> = listOf(
    "CREATE TABLE IF NOT EXISTS `adventure_hypothesis` (`gameId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `text` TEXT NOT NULL, PRIMARY KEY(`gameId`, `position`), FOREIGN KEY(`gameId`) REFERENCES `adventure_game`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_adventure_hypothesis_gameId` ON `adventure_hypothesis` (`gameId`)",
)
