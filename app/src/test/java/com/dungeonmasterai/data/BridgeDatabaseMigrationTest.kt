package com.dungeonmasterai.data

import androidx.room.useReaderConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.dungeonmasterai.domain.character.Attribute
import com.dungeonmasterai.domain.character.Attributes
import com.dungeonmasterai.domain.character.Character
import com.dungeonmasterai.domain.character.CharacterClass
import com.dungeonmasterai.domain.character.Item
import com.dungeonmasterai.domain.game.GameEvent
import com.dungeonmasterai.domain.game.GameState
import com.dungeonmasterai.domain.game.Outcome
import com.dungeonmasterai.domain.game.RejectionReason
import com.dungeonmasterai.domain.scenario.GuardiansBridge
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeDatabaseMigrationTest {
    @Test
    fun migrationCreatesAdventureTablesWithoutTouchingBridgeTables() {
        val bridgeNames = listOf("saved_game", "inventory_item", "history_event")
        assertTrue(ADVENTURE_TABLE_STATEMENTS.isNotEmpty())
        ADVENTURE_TABLE_STATEMENTS.forEach { sql ->
            assertTrue(sql.startsWith("CREATE TABLE") || sql.startsWith("CREATE INDEX"))
            bridgeNames.forEach { name ->
                assertFalse(sql.contains(name))
            }
        }
    }

    @Test
    fun version1FileKeepsTheOriginalBridgeGame() = runBlocking {
        val directory = Files.createTempDirectory("bridge-v1").toFile()
        val file = File(directory, "dungeon-master.db")
        val original = bridgeGame()
        val schema = File("schemas/com.dungeonmasterai.data.AppDatabase/1.json")
        assertTrue("Missing ${schema.absolutePath}", schema.isFile)
        val driver = BundledSQLiteDriver()
        val created = driver.open(file.absolutePath)
        val tableSqlBefore: Map<String, String>
        try {
            created.applyExportedSchema(schema)
            created.insertBridge(original)
            created.execSQL("PRAGMA user_version = 1")
            assertFalse(created.tableExists("adventure_game"))
            assertEquals(1, created.queryLong("PRAGMA user_version").toInt())
            tableSqlBefore = BRIDGE_TABLES.associateWith { name ->
                created.queryText("SELECT sql FROM sqlite_master WHERE name = '$name'")
            }
            assertEquals(1, created.queryLong("SELECT COUNT(*) FROM saved_game").toInt())
            assertEquals(2, created.queryLong("SELECT COUNT(*) FROM inventory_item").toInt())
            assertEquals(original.history.size, created.queryLong("SELECT COUNT(*) FROM history_event").toInt())
        } finally {
            created.close()
        }

        val database = openFileDatabase(file)
        try {
            val restored = RoomSavedGameStore(database.savedGameDao()).load()
            assertEquals(original, restored)
            val tableSqlAfter = BRIDGE_TABLES.associateWith { name ->
                database.queryText("SELECT sql FROM sqlite_master WHERE name = '$name'")
            }
            assertEquals(tableSqlBefore, tableSqlAfter)
            assertTrue(database.queryLong("SELECT COUNT(*) FROM sqlite_master WHERE name = 'adventure_game'") == 1L)
            assertEquals(0, database.queryLong("SELECT COUNT(*) FROM adventure_game").toInt())
            assertEquals(6, database.queryLong("PRAGMA user_version").toInt())
            assertTrue(database.queryLong("SELECT COUNT(*) FROM sqlite_master WHERE name = 'adventure_pending_narration'") == 1L)
            assertTrue(database.queryLong("SELECT COUNT(*) FROM sqlite_master WHERE name = 'adventure_scene_prose'") == 1L)
            assertTrue(file.isFile)
            assertTrue(file.length() > 0)
        } finally {
            database.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun version3AttemptGainsAnEmptyProjectionAndKeepsTheBridge() = runBlocking {
        val directory = Files.createTempDirectory("bridge-v3").toFile()
        val file = File(directory, "dungeon-master.db")
        val schema = File("schemas/com.dungeonmasterai.data.AppDatabase/3.json")
        assertTrue(schema.isFile)
        val driver = BundledSQLiteDriver()
        val created = driver.open(file.absolutePath)
        val bridgeBefore: Map<String, String>
        try {
            created.applyExportedSchema(schema)
            created.insertBridge(bridgeGame())
            created.execSQL(
                "INSERT INTO adventure_game (id, schemaVersion, adventureId, deathEndingId, midnightEventId, " +
                    "name, characterClass, health, strength, agility, intelligence, locationId, minutesElapsed, " +
                    "midnightFired, endingId, worldStamp, narrativeSummary, stateRevision) VALUES " +
                    "(1, 1, 'watch', 'death', 'bells', 'Ada', 'SCHOLAR', 10, 2, 2, 5, 'yard', 0, 0, NULL, 1, '', 0)",
            )
            created.execSQL(
                "INSERT INTO adventure_attempt (gameId, actionId, status, worldStamp) " +
                    "VALUES (1, 'read_archive', 'FAILED', 2)",
            )
            created.execSQL("PRAGMA user_version = 3")
            bridgeBefore = BRIDGE_TABLES.associateWith { name ->
                created.queryText("SELECT sql FROM sqlite_master WHERE name = '$name'")
            }
        } finally {
            created.close()
        }

        val database = openFileDatabase(file)
        try {
            val bridgeAfter = BRIDGE_TABLES.associateWith { name ->
                database.queryText("SELECT sql FROM sqlite_master WHERE name = '$name'")
            }
            assertEquals(bridgeBefore, bridgeAfter)
            assertEquals(6, database.queryLong("PRAGMA user_version").toInt())
            assertEquals(
                "",
                database.queryText("SELECT relevantFacts FROM adventure_attempt WHERE actionId = 'read_archive'"),
            )
            assertEquals(1, database.queryLong("SELECT COUNT(*) FROM saved_game").toInt())
            ADVENTURE_V4_STATEMENTS.forEach { sql ->
                assertTrue(sql.contains("adventure_attempt"))
                BRIDGE_TABLES.forEach { name -> assertFalse(sql.contains(name)) }
            }
        } finally {
            database.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun version4SaveWithoutPresentationRestoresMasculine() = runBlocking {
        val directory = Files.createTempDirectory("bridge-v4").toFile()
        val file = File(directory, "dungeon-master.db")
        val schema = File("schemas/com.dungeonmasterai.data.AppDatabase/4.json")
        assertTrue(schema.isFile)
        val driver = BundledSQLiteDriver()
        val created = driver.open(file.absolutePath)
        try {
            created.applyExportedSchema(schema)
            created.execSQL(
                "INSERT INTO adventure_game (id, schemaVersion, adventureId, deathEndingId, midnightEventId, " +
                    "name, characterClass, health, strength, agility, intelligence, locationId, minutesElapsed, " +
                    "midnightFired, endingId, worldStamp, narrativeSummary, stateRevision) VALUES " +
                    "(1, 1, 'watch', 'death', 'bells', 'Ada', 'SCHOLAR', 10, 2, 2, 5, 'yard', 0, 0, NULL, 1, '', 0)",
            )
            created.execSQL("PRAGMA user_version = 4")
        } finally {
            created.close()
        }

        val database = openFileDatabase(file)
        try {
            assertEquals(6, database.queryLong("PRAGMA user_version").toInt())
            assertEquals(
                "MASCULINE",
                database.queryText("SELECT presentation FROM adventure_game WHERE id = 1"),
            )
            ADVENTURE_V5_STATEMENTS.forEach { sql ->
                assertTrue(sql.contains("adventure_game"))
                BRIDGE_TABLES.forEach { name -> assertFalse(sql.contains(name)) }
            }
        } finally {
            database.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun version5SaveRestoresWithAnEmptyHypothesisList() = runBlocking {
        val directory = Files.createTempDirectory("bridge-v5").toFile()
        val file = File(directory, "dungeon-master.db")
        val schema = File("schemas/com.dungeonmasterai.data.AppDatabase/5.json")
        assertTrue(schema.isFile)
        val driver = BundledSQLiteDriver()
        val created = driver.open(file.absolutePath)
        try {
            created.applyExportedSchema(schema)
            created.insertBridge(bridgeGame())
            created.execSQL(
                "INSERT INTO adventure_game (id, schemaVersion, adventureId, deathEndingId, midnightEventId, " +
                    "name, characterClass, health, strength, agility, intelligence, locationId, minutesElapsed, " +
                    "midnightFired, endingId, worldStamp, narrativeSummary, stateRevision, presentation) VALUES " +
                    "(1, 1, 'watch', 'death', 'bells', 'Ada', 'SCHOLAR', 10, 2, 2, 5, 'yard', 15, 0, NULL, 1, '', 4, " +
                    "'FEMININE')",
            )
            created.execSQL("PRAGMA user_version = 5")
        } finally {
            created.close()
        }

        val database = openFileDatabase(file)
        try {
            assertEquals(6, database.queryLong("PRAGMA user_version").toInt())
            assertEquals(0, database.queryLong("SELECT COUNT(*) FROM adventure_hypothesis").toInt())
            assertEquals("Ada", database.queryText("SELECT name FROM adventure_game WHERE id = 1"))
            assertEquals(15, database.queryLong("SELECT minutesElapsed FROM adventure_game WHERE id = 1").toInt())
            assertEquals(1, database.queryLong("SELECT COUNT(*) FROM saved_game").toInt())
            ADVENTURE_V6_STATEMENTS.forEach { sql ->
                assertTrue(sql.contains("adventure_hypothesis"))
                BRIDGE_TABLES.forEach { name -> assertFalse(sql.contains(name)) }
            }
        } finally {
            database.close()
            directory.deleteRecursively()
        }
    }

    private fun bridgeGame(): GameState {
        val characterClass = CharacterClass.SCOUT
        return GameState(
            character = Character(
                name = "Ada",
                characterClass = characterClass,
                attributes = Attributes.of(characterClass),
                health = 4,
                inventory = listOf(
                    Item(id = "coin_purse", name = "Coin Purse"),
                    Item(id = "rope", name = "Rope"),
                ),
            ),
            sceneId = GuardiansBridge.SCENE_ID,
            outcome = Outcome.ONGOING,
            history = listOf(
                GameEvent.Narration("opening"),
                GameEvent.CheckResolved(
                    attribute = Attribute.AGILITY,
                    roll = 3,
                    modifier = 4,
                    total = 7,
                    difficulty = 12,
                    success = false,
                ),
                GameEvent.HealthChanged(previous = 8, current = 4),
                GameEvent.ItemRemoved(itemId = "coin_purse"),
                GameEvent.ActionRejected(reason = RejectionReason.MISSING_ITEM),
            ),
        )
    }
}

private val BRIDGE_TABLES: List<String> = listOf("saved_game", "inventory_item", "history_event")

private fun SQLiteConnection.applyExportedSchema(schema: File) {
    val database = Json.parseToJsonElement(schema.readText()).jsonObject.getValue("database").jsonObject
    for (element in database.getValue("entities").jsonArray) {
        val entity = element.jsonObject
        val table = entity.getValue("tableName").jsonPrimitive.content
        execSQL(entity.getValue("createSql").jsonPrimitive.content.withTable(table))
        val indices = entity["indices"]
        if (indices is JsonArray) {
            for (index in indices) {
                execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.withTable(table))
            }
        }
    }
    for (query in database.getValue("setupQueries").jsonArray) {
        execSQL(query.jsonPrimitive.content)
    }
}

private fun SQLiteConnection.insertBridge(state: GameState) {
    val rows = state.toRows()
    val game = rows.game
    execSQL(
        "INSERT INTO saved_game (id, name, characterClass, health, sceneId, outcome) VALUES " +
            "(${game.id}, ${game.name.sql()}, ${game.characterClass.sql()}, ${game.health}, " +
            "${game.sceneId.sql()}, ${game.outcome.sql()})",
    )
    rows.inventory.forEach { item ->
        execSQL(
            "INSERT INTO inventory_item (gameId, position, itemId, itemName) VALUES " +
                "(${item.gameId}, ${item.position}, ${item.itemId.sql()}, ${item.itemName.sql()})",
        )
    }
    rows.history.forEach { event ->
        execSQL(
            "INSERT INTO history_event (gameId, position, type, text, attribute, roll, modifier, total, " +
                "difficulty, success, previousHealth, currentHealth, itemId, reason) VALUES (" +
                "${event.gameId}, ${event.position}, ${event.type.sql()}, ${event.text.sql()}, " +
                "${event.attribute.sql()}, ${event.roll.sql()}, ${event.modifier.sql()}, ${event.total.sql()}, " +
                "${event.difficulty.sql()}, ${event.success.sql()}, ${event.previousHealth.sql()}, " +
                "${event.currentHealth.sql()}, ${event.itemId.sql()}, ${event.reason.sql()})",
        )
    }
}

private suspend fun com.dungeonmasterai.data.AppDatabase.queryText(sql: String): String =
    useReaderConnection { connection ->
        connection.usePrepared(sql) { statement ->
            check(statement.step()) { "No row for $sql" }
            statement.getText(0)
        }
    }

private suspend fun com.dungeonmasterai.data.AppDatabase.queryLong(sql: String): Long =
    useReaderConnection { connection ->
        connection.usePrepared(sql) { statement ->
            check(statement.step()) { "No row for $sql" }
            statement.getLong(0)
        }
    }

private fun SQLiteConnection.tableExists(name: String): Boolean =
    queryLong("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = '$name'") > 0

private fun SQLiteConnection.queryLong(sql: String): Long =
    prepare(sql).use { statement ->
        check(statement.step()) { "No row for $sql" }
        statement.getLong(0)
    }

private fun SQLiteConnection.queryText(sql: String): String =
    prepare(sql).use { statement ->
        check(statement.step()) { "No row for $sql" }
        statement.getText(0)
    }

private fun String.withTable(table: String): String = replace("\${TABLE_NAME}", table)

private fun String?.sql(): String = if (this == null) "NULL" else "'${replace("'", "''")}'"

private fun Int?.sql(): String = this?.toString() ?: "NULL"

private fun Boolean?.sql(): String = when (this) {
    null -> "NULL"
    true -> "1"
    false -> "0"
}
