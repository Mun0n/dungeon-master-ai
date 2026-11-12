package com.dungeonmasterai.data

import com.dungeonmasterai.domain.adventure.state.AdventureResolution
import com.dungeonmasterai.domain.adventure.state.AdventureState
import java.io.IOException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MemoryAdventureGameStore : AdventureGameStore {
    var failuresLeft: Int = 0
    private val gate = Mutex()
    private var slot: AdventureRecord? = null

    override suspend fun load(): AdventureRecord? = gate.withLock { slot }

    override suspend fun discard() {
        gate.withLock { slot = null }
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
        val current = slot
        val matches = if (expectedRevision == null) current == null else current?.revision == expectedRevision
        if (!matches) return@withLock AdventureCommit.RevisionConflict
        failIfNeeded()
        val saved = AdventureRecord(
            state = state,
            revision = committedRevision(current?.revision),
            pendingNarration = pendingNarration,
            sceneProse = sceneProse,
            recentInteractions = recentInteractions,
        )
        slot = saved
        AdventureCommit.Saved(saved)
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
        val current = slot ?: return@withLock ResolutionCommit.RevisionConflict
        if (current.revision != expectedRevision) return@withLock ResolutionCommit.RevisionConflict
        val resolution = resolve(current.state)
        if (resolution !is AdventureResolution.Accepted) {
            return@withLock ResolutionCommit.Unchanged(current)
        }
        failIfNeeded()
        val revision = expectedRevision + 1
        val saved = AdventureRecord(
            state = resolution.state,
            revision = revision,
            pendingNarration = pendingNarration(resolution, revision),
            sceneProse = sceneProse,
            recentInteractions = recentInteractions,
        )
        slot = saved
        ResolutionCommit.Saved(saved)
    }

    private fun failIfNeeded() {
        if (failuresLeft > 0) {
            failuresLeft -= 1
            throw IOException("Adventure save failed.")
        }
    }
}
