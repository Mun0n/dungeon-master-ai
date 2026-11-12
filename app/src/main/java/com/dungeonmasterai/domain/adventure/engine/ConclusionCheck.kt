package com.dungeonmasterai.domain.adventure.engine

import com.dungeonmasterai.domain.adventure.model.Deduction
import com.dungeonmasterai.domain.adventure.model.DeductionClaim
import com.dungeonmasterai.domain.adventure.state.AdventureState

enum class ConclusionVerdict {
    ACCEPTED,
    FALSE_ACCUSATION,
    CONTRADICTION,
    PARTIAL,
}

/**
 * A deduction is offered for free text only when Kotlin could call [AdventureEngine.deduce]
 * and the authored claims are complete. The statement itself is not part of the offer.
 */
fun Deduction.offersConclusion(state: AdventureState): Boolean {
    if (statement.isBlank() || localProse.isBlank()) return false
    if (claims.isEmpty() || claims.any { !it.isComplete() }) return false
    if (state.endingId != null || state.character.health == 0) return false
    if (id in state.deductionIds) return false
    return evidenceIds.all { it in state.evidenceIds }
}

fun DeductionClaim.isComplete(): Boolean {
    if (prompt.isBlank() || options.size < 2) return false
    if (options.any { it.gloss.isBlank() }) return false
    if (accept.isEmpty()) return false
    val optionIds = options.map { it.id }.toSet()
    return accept.all { it in optionIds }
}

/**
 * Classifies a well-formed reply. A false accusation wins over a contradiction.
 * Anything else that misses an accepted value is partial, including unknown.
 */
fun classifyConclusion(deduction: Deduction, answers: Map<String, String>): ConclusionVerdict {
    val falseAccusation = deduction.claims.any { claim ->
        val value = answers[claim.id]
        value != null && value in claim.falseValues
    }
    if (falseAccusation) return ConclusionVerdict.FALSE_ACCUSATION
    val contradictory = deduction.contradictions.any { pair ->
        answers[pair.leftClaimId] == pair.leftValue && answers[pair.rightClaimId] == pair.rightValue
    }
    if (contradictory) return ConclusionVerdict.CONTRADICTION
    val accepted = deduction.claims.all { claim -> answers[claim.id] in claim.accept }
    return if (accepted) ConclusionVerdict.ACCEPTED else ConclusionVerdict.PARTIAL
}
