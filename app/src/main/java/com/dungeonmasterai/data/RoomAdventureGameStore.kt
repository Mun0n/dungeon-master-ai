package com.dungeonmasterai.data

import android.util.Log
import com.dungeonmasterai.domain.adventure.state.AdventureResolution
import com.dungeonmasterai.domain.adventure.state.AdventureState
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RoomAdventureGameStore(
    private val dao: AdventureGameDao,
) : AdventureGameStore {
    private val gate = Mutex()

    override suspend fun load(): AdventureRecord? = gate.withLock { read() }

    override suspend fun discard() {
        gate.withLock { dao.clearAll() }
    }

    override suspend fun commit(
        state: AdventureState,
        deathEndingId: String,
        midnightEventId: String,
        expectedRevision: Int?,
        pendingNarration: PendingNarration?,
        sceneProse: Map<String, String>,
        recentInteractions: List<RecentInteraction>,
    ): AdventureCommit = gate.withLock {
        val current = read()
        val matches = if (expectedRevision == null) {
            current == null
        } else {
            current?.revision == expectedRevision
        }
        if (!matches) return@withLock AdventureCommit.RevisionConflict
        val revision = committedRevision(current?.revision)
        AdventureCommit.Saved(
            write(
                state = state,
                revision = revision,
                deathEndingId = deathEndingId,
                midnightEventId = midnightEventId,
                pendingNarration = pendingNarration,
                sceneProse = sceneProse,
                recentInteractions = recentInteractions,
            ),
        )
    }

    override suspend fun commitIfCurrent(
        expectedRevision: Int,
        deathEndingId: String,
        midnightEventId: String,
        sceneProse: Map<String, String>,
        recentInteractions: List<RecentInteraction>,
        resolve: (AdventureState) -> AdventureResolution,
        pendingNarration: (AdventureResolution.Accepted, Int) -> PendingNarration,
    ): ResolutionCommit = gate.withLock {
        val current = read() ?: return@withLock ResolutionCommit.RevisionConflict
        if (current.revision != expectedRevision) return@withLock ResolutionCommit.RevisionConflict
        val resolution = resolve(current.state)
        if (resolution !is AdventureResolution.Accepted) {
            return@withLock ResolutionCommit.Unchanged(current)
        }
        val revision = expectedRevision + 1
        val saved = write(
            state = resolution.state,
            revision = revision,
            deathEndingId = deathEndingId,
            midnightEventId = midnightEventId,
            pendingNarration = pendingNarration(resolution, revision),
            sceneProse = sceneProse,
            recentInteractions = recentInteractions,
        )
        ResolutionCommit.Saved(saved)
    }

    private suspend fun write(
        state: AdventureState,
        revision: Int,
        deathEndingId: String,
        midnightEventId: String,
        pendingNarration: PendingNarration?,
        sceneProse: Map<String, String>,
        recentInteractions: List<RecentInteraction>,
    ): AdventureRecord {
        val pending = pendingNarration?.toEntity()
        pending?.toPending()
        val rows = state.toRows(deathEndingId, midnightEventId, revision).copy(
            pending = pending,
            sceneProse = sceneProse.toSceneEntities(),
            interactions = recentInteractions.toInteractionEntities(),
        )
        try {
            dao.replace(rows)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "Adventure save failed.", error)
            throw error
        }
        return checkNotNull(read()) { "The adventure commit could not be read back." }
    }

    private suspend fun read(): AdventureRecord? {
        val rows = dao.loadRows() ?: return null
        if (rows.game.stateRevision < 0) {
            dao.clearAll()
            return null
        }
        val state = try {
            rows.toAdventureState()
        } catch (error: IllegalArgumentException) {
            dao.clearAll()
            return null
        }
        val pending = rows.pending?.let { entity ->
            try {
                entity.toPending()
            } catch (error: IllegalArgumentException) {
                dao.clearPending()
                null
            }
        }
        return AdventureRecord(
            state = state,
            revision = rows.game.stateRevision,
            pendingNarration = pending,
            sceneProse = rows.sceneProse.mapNotNull { entity ->
                val text = entity.text.trim()
                if (text.isEmpty()) null else entity.sceneContractId to text
            }.toMap(),
            recentInteractions = rows.interactions.mapNotNull { entity ->
                if (entity.speaker != "player" && entity.speaker != "narration") {
                    null
                } else if (entity.text.isBlank()) {
                    null
                } else {
                    RecentInteraction(speaker = entity.speaker, text = entity.text)
                }
            },
        )
    }

    private companion object {
        const val TAG: String = "AdventureGameStore"
    }
}
