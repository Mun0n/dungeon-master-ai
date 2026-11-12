package com.dungeonmasterai.presentation

import com.dungeonmasterai.domain.adventure.model.Adventure
import com.dungeonmasterai.domain.adventure.state.AdventureState
import com.dungeonmasterai.domain.adventure.state.clockFace

/**
 * Rules text for Inés. It reads the confirmed state.
 * A protection flag set in the same resolution as the bells does not count as a rescue.
 */
fun inesClockLine(state: AdventureState): String? {
    if (state.adventureId != VALDUMBRIA_ID) return null
    val protected = state.flags[FLAG_PROTECTED] == true
    val fate = state.npcStates[NPC_INES]
    if (!state.midnightFired) {
        if (!protected) return null
        return PlayerCopy.INES_WARNED
    }
    return when (fate) {
        STATE_SAVED -> PlayerCopy.INES_SAVED
        STATE_LOST -> if (protected) {
            PlayerCopy.INES_LOST_WITH_BELLS
        } else {
            PlayerCopy.INES_LOST
        }
        else -> PlayerCopy.INES_UNRECORDED
    }
}

data class RevealedFacts(
    val clues: List<String>,
    val evidence: List<String>,
)

fun revealedFacts(adventure: Adventure, state: AdventureState): RevealedFacts = RevealedFacts(
    clues = adventure.clues.filter { it.id in state.clueIds }.map { it.text },
    evidence = adventure.evidences.filter { it.id in state.evidenceIds }.map { it.text },
)

fun clockLabel(state: AdventureState): String {
    val face = clockFace(state.minutesElapsed)
    return "%02d:%02d".format(face.hour, face.minute)
}

const val VALDUMBRIA_ID: String = "valdumbria"

private const val FLAG_PROTECTED: String = "ines_protected"
private const val NPC_INES: String = "ines"
private const val STATE_SAVED: String = "saved"
private const val STATE_LOST: String = "lost"
