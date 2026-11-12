package com.dungeonmasterai.data

import androidx.room.execSQL
import androidx.room.useWriterConnection
import com.dungeonmasterai.domain.adventure.state.AdventureResolution
import com.dungeonmasterai.domain.character.Attributes
import com.dungeonmasterai.domain.character.Character
import com.dungeonmasterai.domain.character.CharacterClass
import com.dungeonmasterai.domain.character.Item
import com.dungeonmasterai.domain.game.GameEvent
import com.dungeonmasterai.domain.game.GameState
import com.dungeonmasterai.domain.game.Outcome
import com.dungeonmasterai.domain.scenario.GuardiansBridge
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AdventureGameStoreTest {
    @Test
    fun saveAndLoadRestoreTheConfirmedState() = runBlocking {
        val database = openMemoryDatabase()
        try {
            val store = RoomAdventureGameStore(database.adventureGameDao())
            val state = confirmedAdventure()

            store.commitNew(state)

            val loaded = store.load()
            assertEquals(state, loaded?.state)
            assertEquals(0, loaded?.revision)
            assertEquals("The bell rang.", loaded?.state?.narrativeSummary)
        } finally {
            database.close()
        }
    }

    @Test
    fun discardRemovesTheAdventureSoTheNextGameStartsAtRevisionZero() = runBlocking {
        val database = openMemoryDatabase()
        try {
            val store = RoomAdventureGameStore(database.adventureGameDao())
            val state = confirmedAdventure()
            store.commitNew(state)

            store.discard()

            assertNull(store.load())
            store.commitNew(state)
            assertEquals(0, store.load()?.revision)
            assertEquals(state, store.load()?.state)
        } finally {
            database.close()
        }
    }

    @Test
    fun anInterruptedActionRestoresTheLastCommit() = runBlocking {
        val database = openMemoryDatabase()
        try {
            val store = RoomAdventureGameStore(database.adventureGameDao())
            val confirmed = confirmedAdventure()
            store.commitNew(confirmed)
            val interrupted = confirmed.copy(
                minutesElapsed = confirmed.minutesElapsed + 10,
                evidenceIds = confirmed.evidenceIds + "uncommitted_mark",
                character = confirmed.character.copy(health = confirmed.character.health - 1),
            )

            val restored = store.load()

            assertEquals(confirmed, restored?.state)
            assertEquals(130, restored?.state?.minutesElapsed)
            assertFalse(restored?.state?.evidenceIds?.contains("uncommitted_mark") == true)
            assertTrue(interrupted.evidenceIds.contains("uncommitted_mark"))
            assertEquals(confirmed.attempts, restored?.state?.attempts)
            assertEquals(confirmed.midnightFired, restored?.state?.midnightFired)
        } finally {
            database.close()
        }
    }

    @Test
    fun aFailedWriteKeepsTheConfirmedState() = runBlocking {
        val database = openMemoryDatabase()
        try {
            val store = RoomAdventureGameStore(database.adventureGameDao())
            val confirmed = confirmedAdventure()
            var memory = confirmed
            store.commitNew(memory)
            val broken = memory.toRows(DEATH_ENDING, MIDNIGHT_EVENT).let { rows ->
                val first = rows.items.first()
                rows.copy(items = rows.items + first.copy(itemId = "lantern", itemName = "Lantern"))
            }

            val error = assertThrows(Exception::class.java) {
                runBlocking { database.adventureGameDao().replace(broken) }
            }
            assertTrue(error.toString(), error !is IllegalArgumentException)

            assertEquals(confirmed, memory)
            assertEquals(confirmed, store.load()?.state)
        } finally {
            database.close()
        }
    }

    @Test
    fun aCorruptAdventureSnapshotIsDroppedAndTheBridgeSaveRemains() = runBlocking {
        val database = openMemoryDatabase()
        try {
            val bridge = bridgeState()
            val adventures = RoomAdventureGameStore(database.adventureGameDao())
            val bridges = RoomSavedGameStore(database.savedGameDao())
            bridges.save(bridge)
            adventures.commitNew(deadAdventure())
            database.useWriterConnection { connection ->
                connection.execSQL("UPDATE adventure_game SET health = 5 WHERE id = $ADVENTURE_GAME_ID")
            }

            assertNull(adventures.load())
            assertEquals(bridge, bridges.load())
        } finally {
            database.close()
        }
    }

    @Test
    fun deathRestoresOnlyWhenHealthIsZero() = runBlocking {
        val database = openMemoryDatabase()
        try {
            val store = RoomAdventureGameStore(database.adventureGameDao())
            val dead = deadAdventure()
            store.commitNew(dead)

            assertEquals(dead, store.load()?.state)

            database.useWriterConnection { connection ->
                connection.execSQL("UPDATE adventure_game SET health = 4 WHERE id = $ADVENTURE_GAME_ID")
            }

            assertNull(store.load())
        } finally {
            database.close()
        }
    }

    @Test
    fun twoCommitsOfTheSameRevisionCannotBothSave() = runBlocking {
        val database = openMemoryDatabase()
        try {
            val store = RoomAdventureGameStore(database.adventureGameDao())
            val confirmed = confirmedAdventure()
            store.commitNew(confirmed)
            val calls = AtomicInteger(0)
            val results = coroutineScope {
                listOf("one", "two").map { summary ->
                    async(Dispatchers.Default) {
                        store.commitIfCurrent(
                            expectedRevision = 0,
                            deathEndingId = DEATH_ENDING,
                            midnightEventId = MIDNIGHT_EVENT,
                            sceneProse = emptyMap(),
                            recentInteractions = emptyList(),
                            resolve = { state ->
                                calls.incrementAndGet()
                                AdventureResolution.Accepted(
                                    state = state.copy(narrativeSummary = summary),
                                    success = true,
                                    check = null,
                                )
                            },
                            pendingNarration = { _, revision -> awaitingNarration(revision) },
                        )
                    }
                }.awaitAll()
            }

            assertEquals(1, calls.get())
            assertEquals(1, results.count { it is ResolutionCommit.Saved })
            assertEquals(1, results.count { it is ResolutionCommit.RevisionConflict })
            val loaded = store.load()
            assertEquals(1, loaded?.revision)
            assertEquals(NarrationDelivery.AWAITING, loaded?.pendingNarration?.delivery)
            assertTrue(loaded?.state?.narrativeSummary == "one" || loaded?.state?.narrativeSummary == "two")
        } finally {
            database.close()
        }
    }

    @Test
    fun aStaleNarrationOrSceneCannotOverwriteANewerWrite() = runBlocking {
        val database = openMemoryDatabase()
        try {
            val store = RoomAdventureGameStore(database.adventureGameDao())
            val confirmed = confirmedAdventure()
            store.commitNew(confirmed)
            val results = coroutineScope {
                listOf("The first bell.", "The second bell.").map { text ->
                    async(Dispatchers.Default) {
                        store.commit(
                            state = confirmed,
                            deathEndingId = DEATH_ENDING,
                            midnightEventId = MIDNIGHT_EVENT,
                            expectedRevision = 0,
                            pendingNarration = awaitingNarration(1).copy(
                                delivery = NarrationDelivery.VALIDATED,
                                text = text,
                            ),
                            sceneProse = mapOf("yard_contract" to text),
                            recentInteractions = listOf(RecentInteraction("narration", text)),
                        )
                    }
                }.awaitAll()
            }

            assertEquals(1, results.count { it is AdventureCommit.Saved })
            assertEquals(1, results.count { it is AdventureCommit.RevisionConflict })
            val loaded = store.load()
            val winner = (results.first { it is AdventureCommit.Saved } as AdventureCommit.Saved).record
            assertEquals(1, loaded?.revision)
            assertEquals(confirmed, loaded?.state)
            assertEquals(winner.pendingNarration?.text, loaded?.pendingNarration?.text)
            assertEquals(winner.sceneProse, loaded?.sceneProse)

            val stale = store.commit(
                state = confirmed,
                deathEndingId = DEATH_ENDING,
                midnightEventId = MIDNIGHT_EVENT,
                expectedRevision = 0,
                pendingNarration = awaitingNarration(1).copy(
                    delivery = NarrationDelivery.VALIDATED,
                    text = "A late line.",
                ),
                sceneProse = mapOf("yard_contract" to "A late scene."),
                recentInteractions = emptyList(),
            )
            assertTrue(stale is AdventureCommit.RevisionConflict)
            assertEquals(winner.pendingNarration?.text, store.load()?.pendingNarration?.text)
            assertEquals(winner.sceneProse, store.load()?.sceneProse)
        } finally {
            database.close()
        }
    }

    @Test
    fun aValidatedNarrationRestoresWithoutTheRulesChanging() = runBlocking {
        val database = openMemoryDatabase()
        try {
            val store = RoomAdventureGameStore(database.adventureGameDao())
            val confirmed = confirmedAdventure()
            store.commitNew(confirmed)
            val validated = awaitingNarration(1).copy(
                delivery = NarrationDelivery.VALIDATED,
                text = "The bell is still ringing.",
            )
            val saved = store.commit(
                state = confirmed,
                deathEndingId = DEATH_ENDING,
                midnightEventId = MIDNIGHT_EVENT,
                expectedRevision = 0,
                pendingNarration = validated,
                sceneProse = mapOf("yard_contract" to "The yard is empty."),
                recentInteractions = listOf(RecentInteraction("narration", "The bell is still ringing.")),
            )

            val loaded = store.load()
            assertTrue(saved is AdventureCommit.Saved)
            assertEquals(confirmed, loaded?.state)
            assertEquals(1, loaded?.revision)
            assertEquals("The bell is still ringing.", loaded?.pendingNarration?.text)
            assertEquals("The yard is empty.", loaded?.sceneProse?.get("yard_contract"))
        } finally {
            database.close()
        }
    }

    @Test
    fun aCorruptPendingNarrationIsDroppedAndTheConfirmedStateRemains() = runBlocking {
        val database = openMemoryDatabase()
        try {
            val store = RoomAdventureGameStore(database.adventureGameDao())
            val confirmed = confirmedAdventure()
            store.commitNew(confirmed)
            database.useWriterConnection { connection ->
                connection.execSQL(
                    "INSERT INTO adventure_pending_narration (" +
                        "gameId, status, requestId, stateRevision, sceneContractId, actionId, outcome, success, " +
                        "attribute, roll, modifier, total, difficulty, checkSuccess, appliedEffectsJson, text" +
                        ") VALUES (" +
                        "1, 'AWAITING', 'req', 'r0', 'yard_contract', 'search_yard', 'SUCCEEDED', 1, " +
                        "NULL, NULL, NULL, NULL, NULL, NULL, 'not-json', NULL)",
                )
            }

            val loaded = store.load()

            assertEquals(confirmed, loaded?.state)
            assertNull(loaded?.pendingNarration)
            assertNull(store.load()?.pendingNarration)
        } finally {
            database.close()
        }
    }

    private fun bridgeState(): GameState {
        val characterClass = CharacterClass.SCOUT
        return GameState(
            character = Character(
                name = "Ada",
                characterClass = characterClass,
                attributes = Attributes.of(characterClass),
                health = 4,
                inventory = listOf(Item(id = "coin_purse", name = "Coin Purse")),
            ),
            sceneId = GuardiansBridge.SCENE_ID,
            outcome = Outcome.ONGOING,
            history = listOf(GameEvent.Narration("opening")),
        )
    }
}

private suspend fun AdventureGameStore.commitNew(state: com.dungeonmasterai.domain.adventure.state.AdventureState) {
    val saved = commit(
        state = state,
        deathEndingId = DEATH_ENDING,
        midnightEventId = MIDNIGHT_EVENT,
        expectedRevision = null,
        pendingNarration = null,
        sceneProse = emptyMap(),
        recentInteractions = emptyList(),
    )
    check(saved is AdventureCommit.Saved)
}

private fun awaitingNarration(revision: Int): PendingNarration = PendingNarration(
    delivery = NarrationDelivery.AWAITING,
    requestId = "req$revision",
    stateRevision = revisionToken(revision),
    sceneContractId = "yard_contract",
    actionId = "search_yard",
    outcome = "SUCCEEDED",
    success = true,
    attribute = null,
    check = null,
    appliedEffects = emptyList(),
    text = null,
)
