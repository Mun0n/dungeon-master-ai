package com.dungeonmasterai.presentation.dice

import com.dungeonmasterai.presentation.CheckReading

data class PendingThrow(
    val actionId: String,
    val playerText: String,
    val attribute: String,
    val modifier: Int,
    val difficulty: Int,
    val revision: Int,
)

sealed interface DiceOverlay {
    data object Closed : DiceOverlay

    data class Waiting(val pending: PendingThrow) : DiceOverlay

    data class Throwing(val pending: PendingThrow) : DiceOverlay

    data class Playing(
        val pending: PendingThrow,
        val reading: CheckReading,
        val startedAtMs: Long,
    ) : DiceOverlay

    data class Holding(
        val pending: PendingThrow,
        val reading: CheckReading,
    ) : DiceOverlay
}

/**
 * Phases of one cinematic roll. A throw can be requested only from [DiceOverlay.Waiting].
 */
internal class DiceOverlayModel {
    var overlay: DiceOverlay = DiceOverlay.Closed
        private set

    fun open(pending: PendingThrow) {
        check(overlay is DiceOverlay.Closed) { "A roll is already open." }
        overlay = DiceOverlay.Waiting(pending)
    }

    fun requestThrow(): PendingThrow? {
        val waiting = overlay as? DiceOverlay.Waiting ?: return null
        overlay = DiceOverlay.Throwing(waiting.pending)
        return waiting.pending
    }

    fun committed(reading: CheckReading, nowMs: Long) {
        val throwing = overlay as? DiceOverlay.Throwing ?: return
        overlay = DiceOverlay.Playing(throwing.pending, reading, nowMs)
    }

    fun hold() {
        val playing = overlay as? DiceOverlay.Playing ?: return
        overlay = DiceOverlay.Holding(playing.pending, playing.reading)
    }

    fun close() {
        overlay = DiceOverlay.Closed
    }

    fun keepAfterRestore(revision: Int?): Boolean {
        val waiting = overlay as? DiceOverlay.Waiting
        if (waiting != null && (revision == null || revision != waiting.pending.revision)) {
            overlay = DiceOverlay.Closed
            return false
        }
        return overlay !is DiceOverlay.Closed
    }
}
