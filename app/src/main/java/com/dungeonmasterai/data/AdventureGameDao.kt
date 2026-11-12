package com.dungeonmasterai.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

private const val GAME_ID: String = "$ADVENTURE_GAME_ID"

@Dao
abstract class AdventureGameDao {
    @Query("SELECT * FROM adventure_game WHERE id = $GAME_ID")
    abstract suspend fun loadGame(): AdventureGameEntity?

    @Query("SELECT * FROM adventure_item WHERE gameId = $GAME_ID ORDER BY position")
    abstract suspend fun loadItems(): List<AdventureItemEntity>

    @Query("SELECT * FROM adventure_flag WHERE gameId = $GAME_ID ORDER BY flagId")
    abstract suspend fun loadFlags(): List<AdventureFlagEntity>

    @Query("SELECT * FROM adventure_discovery WHERE gameId = $GAME_ID ORDER BY locationId")
    abstract suspend fun loadDiscoveries(): List<AdventureDiscoveryEntity>

    @Query("SELECT * FROM adventure_evidence WHERE gameId = $GAME_ID ORDER BY evidenceId")
    abstract suspend fun loadEvidence(): List<AdventureEvidenceEntity>

    @Query("SELECT * FROM adventure_clue WHERE gameId = $GAME_ID ORDER BY clueId")
    abstract suspend fun loadClues(): List<AdventureClueEntity>

    @Query("SELECT * FROM adventure_relationship WHERE gameId = $GAME_ID ORDER BY npcId")
    abstract suspend fun loadRelationships(): List<AdventureRelationshipEntity>

    @Query("SELECT * FROM adventure_npc_state WHERE gameId = $GAME_ID ORDER BY npcId")
    abstract suspend fun loadNpcStates(): List<AdventureNpcStateEntity>

    @Query("SELECT * FROM adventure_attempt WHERE gameId = $GAME_ID ORDER BY actionId")
    abstract suspend fun loadAttempts(): List<AdventureAttemptEntity>

    @Query("SELECT * FROM adventure_fired_event WHERE gameId = $GAME_ID ORDER BY eventId")
    abstract suspend fun loadFiredEvents(): List<AdventureFiredEventEntity>

    @Query("SELECT * FROM adventure_deduction WHERE gameId = $GAME_ID ORDER BY deductionId")
    abstract suspend fun loadDeductions(): List<AdventureDeductionEntity>

    @Query("SELECT * FROM adventure_hypothesis WHERE gameId = $GAME_ID ORDER BY position")
    abstract suspend fun loadHypotheses(): List<AdventureHypothesisEntity>

    @Query("SELECT * FROM adventure_log WHERE gameId = $GAME_ID ORDER BY position")
    abstract suspend fun loadHistory(): List<AdventureLogEntity>

    @Query("SELECT * FROM adventure_pending_narration WHERE gameId = $GAME_ID")
    abstract suspend fun loadPending(): AdventurePendingNarrationEntity?

    @Query("SELECT * FROM adventure_scene_prose WHERE gameId = $GAME_ID ORDER BY sceneContractId")
    abstract suspend fun loadSceneProse(): List<AdventureSceneProseEntity>

    @Query("SELECT * FROM adventure_interaction WHERE gameId = $GAME_ID ORDER BY position")
    abstract suspend fun loadInteractions(): List<AdventureInteractionEntity>

    @Insert
    abstract suspend fun insertGame(game: AdventureGameEntity)

    @Insert
    abstract suspend fun insertItems(items: List<AdventureItemEntity>)

    @Insert
    abstract suspend fun insertFlags(flags: List<AdventureFlagEntity>)

    @Insert
    abstract suspend fun insertDiscoveries(discoveries: List<AdventureDiscoveryEntity>)

    @Insert
    abstract suspend fun insertEvidence(evidence: List<AdventureEvidenceEntity>)

    @Insert
    abstract suspend fun insertClues(clues: List<AdventureClueEntity>)

    @Insert
    abstract suspend fun insertRelationships(relationships: List<AdventureRelationshipEntity>)

    @Insert
    abstract suspend fun insertNpcStates(states: List<AdventureNpcStateEntity>)

    @Insert
    abstract suspend fun insertAttempts(attempts: List<AdventureAttemptEntity>)

    @Insert
    abstract suspend fun insertFiredEvents(events: List<AdventureFiredEventEntity>)

    @Insert
    abstract suspend fun insertDeductions(deductions: List<AdventureDeductionEntity>)

    @Insert
    abstract suspend fun insertHypotheses(hypotheses: List<AdventureHypothesisEntity>)

    @Insert
    abstract suspend fun insertHistory(history: List<AdventureLogEntity>)

    @Insert
    abstract suspend fun insertPending(pending: AdventurePendingNarrationEntity)

    @Insert
    abstract suspend fun insertSceneProse(prose: List<AdventureSceneProseEntity>)

    @Insert
    abstract suspend fun insertInteractions(interactions: List<AdventureInteractionEntity>)

    @Query("DELETE FROM adventure_interaction")
    abstract suspend fun clearInteractions()

    @Query("DELETE FROM adventure_scene_prose")
    abstract suspend fun clearSceneProse()

    @Query("DELETE FROM adventure_pending_narration")
    abstract suspend fun clearPending()

    @Query("DELETE FROM adventure_log")
    abstract suspend fun clearHistory()

    @Query("DELETE FROM adventure_deduction")
    abstract suspend fun clearDeductions()

    @Query("DELETE FROM adventure_hypothesis")
    abstract suspend fun clearHypotheses()

    @Query("DELETE FROM adventure_fired_event")
    abstract suspend fun clearFiredEvents()

    @Query("DELETE FROM adventure_attempt")
    abstract suspend fun clearAttempts()

    @Query("DELETE FROM adventure_npc_state")
    abstract suspend fun clearNpcStates()

    @Query("DELETE FROM adventure_relationship")
    abstract suspend fun clearRelationships()

    @Query("DELETE FROM adventure_clue")
    abstract suspend fun clearClues()

    @Query("DELETE FROM adventure_evidence")
    abstract suspend fun clearEvidence()

    @Query("DELETE FROM adventure_discovery")
    abstract suspend fun clearDiscoveries()

    @Query("DELETE FROM adventure_flag")
    abstract suspend fun clearFlags()

    @Query("DELETE FROM adventure_item")
    abstract suspend fun clearItems()

    @Query("DELETE FROM adventure_game")
    abstract suspend fun clearGame()

    @Transaction
    open suspend fun loadRows(): AdventureRows? {
        val game = loadGame() ?: return null
        return AdventureRows(
            game = game,
            items = loadItems(),
            flags = loadFlags(),
            discoveries = loadDiscoveries(),
            evidence = loadEvidence(),
            clues = loadClues(),
            relationships = loadRelationships(),
            npcStates = loadNpcStates(),
            attempts = loadAttempts(),
            firedEvents = loadFiredEvents(),
            deductions = loadDeductions(),
            hypotheses = loadHypotheses(),
            history = loadHistory(),
            pending = loadPending(),
            sceneProse = loadSceneProse(),
            interactions = loadInteractions(),
        )
    }

    @Transaction
    open suspend fun replace(rows: AdventureRows) {
        clearChildren()
        clearGame()
        insertGame(rows.game)
        if (rows.items.isNotEmpty()) insertItems(rows.items)
        if (rows.flags.isNotEmpty()) insertFlags(rows.flags)
        if (rows.discoveries.isNotEmpty()) insertDiscoveries(rows.discoveries)
        if (rows.evidence.isNotEmpty()) insertEvidence(rows.evidence)
        if (rows.clues.isNotEmpty()) insertClues(rows.clues)
        if (rows.relationships.isNotEmpty()) insertRelationships(rows.relationships)
        if (rows.npcStates.isNotEmpty()) insertNpcStates(rows.npcStates)
        if (rows.attempts.isNotEmpty()) insertAttempts(rows.attempts)
        if (rows.firedEvents.isNotEmpty()) insertFiredEvents(rows.firedEvents)
        if (rows.deductions.isNotEmpty()) insertDeductions(rows.deductions)
        if (rows.hypotheses.isNotEmpty()) insertHypotheses(rows.hypotheses)
        if (rows.history.isNotEmpty()) insertHistory(rows.history)
        rows.pending?.let { insertPending(it) }
        if (rows.sceneProse.isNotEmpty()) insertSceneProse(rows.sceneProse)
        if (rows.interactions.isNotEmpty()) insertInteractions(rows.interactions)
    }

    @Transaction
    open suspend fun clearAll() {
        clearChildren()
        clearGame()
    }

    private suspend fun clearChildren() {
        clearInteractions()
        clearSceneProse()
        clearPending()
        clearHistory()
        clearHypotheses()
        clearDeductions()
        clearFiredEvents()
        clearAttempts()
        clearNpcStates()
        clearRelationships()
        clearClues()
        clearEvidence()
        clearDiscoveries()
        clearFlags()
        clearItems()
    }
}
