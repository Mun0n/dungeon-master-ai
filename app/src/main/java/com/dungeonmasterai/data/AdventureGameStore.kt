package com.dungeonmasterai.data

import com.dungeonmasterai.domain.adventure.model.Effect
import com.dungeonmasterai.domain.adventure.state.AdventureResolution
import com.dungeonmasterai.domain.adventure.state.AdventureState
import com.dungeonmasterai.domain.dice.CheckResult

/**
 * One confirmed adventure plus the narration that may still be waiting to be shown.
 * The rules state and a validated narration are stored before the session publishes them.
 */
data class AdventureRecord(
    val state: AdventureState,
    val revision: Int,
    val pendingNarration: PendingNarration?,
    val sceneProse: Map<String, String>,
    val recentInteractions: List<RecentInteraction>,
)

data class RecentInteraction(
    val speaker: String,
    val text: String,
)

enum class NarrationDelivery {
    /** The resolution is committed. No validated narration is stored yet. */
    AWAITING,

    /** The narration text was validated and stored. Showing it does not call the model. */
    VALIDATED,
}

data class PendingNarration(
    val delivery: NarrationDelivery,
    val requestId: String,
    val stateRevision: String,
    val sceneContractId: String,
    val actionId: String,
    val outcome: String,
    val success: Boolean,
    val attribute: String?,
    val check: CheckResult?,
    val appliedEffects: List<Effect>,
    val text: String?,
)

sealed interface AdventureCommit {
    data class Saved(val record: AdventureRecord) : AdventureCommit
    data object RevisionConflict : AdventureCommit
}

sealed interface ResolutionCommit {
    data class Saved(val record: AdventureRecord) : ResolutionCommit
    data class Unchanged(val record: AdventureRecord) : ResolutionCommit
    data object RevisionConflict : ResolutionCommit
}

/**
 * Reads and writes one confirmed adventure snapshot.
 * It does not roll dice or apply effects.
 * [commitIfCurrent] is the only path that resolves: the revision check and the
 * rules call share one lock, so two turns cannot resolve the same revision.
 * Every successful [commit] of an existing adventure advances the revision.
 * A caller that still holds the previous revision does not write.
 */
interface AdventureGameStore {
    suspend fun load(): AdventureRecord?

    /**
     * Deletes the one adventure snapshot. The bridge save is a different store.
     */
    suspend fun discard()

    suspend fun commit(
        state: AdventureState,
        deathEndingId: String,
        midnightEventId: String,
        expectedRevision: Int?,
        pendingNarration: PendingNarration?,
        sceneProse: Map<String, String>,
        recentInteractions: List<RecentInteraction>,
    ): AdventureCommit

    suspend fun commitIfCurrent(
        expectedRevision: Int,
        deathEndingId: String,
        midnightEventId: String,
        sceneProse: Map<String, String>,
        recentInteractions: List<RecentInteraction>,
        resolve: (AdventureState) -> AdventureResolution,
        pendingNarration: (AdventureResolution.Accepted, Int) -> PendingNarration,
    ): ResolutionCommit
}

fun revisionToken(revision: Int): String {
    require(revision >= 0) { "Revision must be >= 0." }
    return "r$revision"
}

/**
 * A new game is revision 0. Every later successful commit advances the revision,
 * including validated narration and generated scene prose.
 */
internal fun committedRevision(currentRevision: Int?): Int =
    if (currentRevision == null) 0 else currentRevision + 1
