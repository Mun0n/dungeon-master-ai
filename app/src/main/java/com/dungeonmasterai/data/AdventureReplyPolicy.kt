package com.dungeonmasterai.data

import com.dungeonmasterai.domain.adventure.model.AdventureRules
import com.dungeonmasterai.domain.adventure.narration.OfferedConclusion
import com.dungeonmasterai.domain.adventure.narration.containsListedPhrase
import com.dungeonmasterai.domain.adventure.narration.containsPlayerTechnicalToken
import com.dungeonmasterai.domain.adventure.narration.redactSummary
import java.util.Locale

internal sealed interface InterpretationDecision {
    data class Recognized(val actionId: String) : InterpretationDecision
    data class Conclusion(
        val deductionId: String,
        val answers: List<Pair<String, String>>,
    ) : InterpretationDecision
    data object Declined : InterpretationDecision
    data object Unusable : InterpretationDecision
    data object UnusableConclusion : InterpretationDecision
}

internal sealed interface NarrationDecision {
    data class Accepted(val text: String, val summary: String?) : NarrationDecision
    data object Unusable : NarrationDecision
}

internal fun interpretReply(
    call: BackendCall<InterpretationResponse>,
    requestId: String,
    stateRevision: String,
    sceneContractId: String,
    available: Set<String>,
    unavailable: Set<String>,
    blocked: Set<String>,
    offered: List<OfferedConclusion>,
): InterpretationDecision {
    val body = (call as? BackendCall.Ready)?.body ?: return InterpretationDecision.Unusable
    if (!body.matches(requestId, stateRevision, sceneContractId)) return InterpretationDecision.Unusable
    if (body.status == "CONCLUSION") return conclusionDecision(body, offered)
    if (body.parameters.isNotEmpty()) return InterpretationDecision.Unusable
    if (body.claims.isNotEmpty()) return InterpretationDecision.Unusable
    val one = body.candidateActionId
    val many = body.candidateActionIds
    val distinct = many.size == many.toSet().size
    return when (body.status) {
        "RECOGNIZED" -> {
            if (one != null && one in available && many.isEmpty()) {
                InterpretationDecision.Recognized(one)
            } else {
                InterpretationDecision.Unusable
            }
        }
        "AMBIGUOUS" -> {
            if (one == null && many.size >= 2 && distinct && many.all { it in available }) {
                InterpretationDecision.Declined
            } else {
                InterpretationDecision.Unusable
            }
        }
        "UNRECOGNIZED" -> {
            if (one == null && many.isEmpty()) InterpretationDecision.Declined else InterpretationDecision.Unusable
        }
        "UNAVAILABLE" -> {
            if (one != null && one in unavailable && many.isEmpty()) {
                InterpretationDecision.Declined
            } else {
                InterpretationDecision.Unusable
            }
        }
        "REQUIREMENTS_REJECTED" -> {
            if (one != null && one in blocked && many.isEmpty()) {
                InterpretationDecision.Declined
            } else {
                InterpretationDecision.Unusable
            }
        }
        else -> InterpretationDecision.Unusable
    }
}

private fun conclusionDecision(
    body: InterpretationResponse,
    offered: List<OfferedConclusion>,
): InterpretationDecision {
    if (body.candidateActionId != null || body.candidateActionIds.isNotEmpty() || body.parameters.isNotEmpty()) {
        return InterpretationDecision.UnusableConclusion
    }
    val conclusion = offered.singleOrNull() ?: return InterpretationDecision.UnusableConclusion
    val expected = conclusion.claims.associate { claim -> claim.id to claim.options.map { it.id }.toSet() }
    if (body.claims.size != expected.size) return InterpretationDecision.UnusableConclusion
    val answers = mutableListOf<Pair<String, String>>()
    val seen = mutableSetOf<String>()
    for (claim in body.claims) {
        if (!seen.add(claim.claimId)) return InterpretationDecision.UnusableConclusion
        val options = expected[claim.claimId] ?: return InterpretationDecision.UnusableConclusion
        if (claim.optionId !in options) return InterpretationDecision.UnusableConclusion
        answers += claim.claimId to claim.optionId
    }
    if (seen != expected.keys) return InterpretationDecision.UnusableConclusion
    return InterpretationDecision.Conclusion(conclusion.id, answers)
}

internal fun narrationReply(
    call: BackendCall<NarrationResponse>,
    requestId: String,
    stateRevision: String,
    sceneContractId: String,
    outcome: String,
    forbidden: List<String>,
    technicalIds: List<String>,
): NarrationDecision {
    val body = (call as? BackendCall.Ready)?.body ?: return NarrationDecision.Unusable
    if (!body.matches(requestId, stateRevision, sceneContractId)) return NarrationDecision.Unusable
    if (body.outcome != outcome) return NarrationDecision.Unusable
    val text = body.text.trim()
    if (text.isEmpty() || text.length > AdventureRules.LOCAL_PROSE_MAX) return NarrationDecision.Unusable
    if (body.auxiliary.keys.any { it != "narrativeSummary" }) return NarrationDecision.Unusable
    val summary = body.auxiliary["narrativeSummary"]?.trim()?.takeIf { it.isNotEmpty() }
    if (summary != null && summary.length > AdventureRules.SUMMARY_MAX) {
        return NarrationDecision.Unusable
    }
    if (containsListedPhrase(text, forbidden) || containsPlayerTechnicalToken(text)) {
        return NarrationDecision.Unusable
    }
    if (technicalIds.any { identifier -> identifier.isNotBlank() && identifier in text }) {
        return NarrationDecision.Unusable
    }
    val cleaned = summary?.let { redactSummary(it, forbidden) }?.takeIf { it.isNotEmpty() }
    return NarrationDecision.Accepted(text = text, summary = cleaned)
}

internal fun sceneReply(
    call: BackendCall<SceneResponse>,
    requestId: String,
    stateRevision: String,
    sceneContractId: String,
    forbidden: List<String>,
    technicalIds: List<String>,
): String? {
    val body = (call as? BackendCall.Ready)?.body ?: return null
    if (!body.matches(requestId, stateRevision, sceneContractId)) return null
    if (body.auxiliary.isNotEmpty()) return null
    val text = body.text.trim()
    if (text.isEmpty() || text.length > AdventureRules.SCENE_TEXT_MAX) return null
    if (containsListedPhrase(text, forbidden) || containsPlayerTechnicalToken(text)) return null
    if (technicalIds.any { identifier -> identifier.isNotBlank() && identifier in text }) return null
    return text
}

internal fun localActionId(text: String, available: Set<String>): String? {
    val folded = text.trim().lowercase(Locale.ROOT)
    if (folded.isEmpty()) return null
    return available.filter { it.lowercase(Locale.ROOT) == folded }.singleOrNull()
}

private fun InterpretationResponse.matches(
    requestId: String,
    stateRevision: String,
    sceneContractId: String,
): Boolean = contractVersion == CONTRACT_VERSION &&
    this.requestId == requestId &&
    this.stateRevision == stateRevision &&
    this.sceneContractId == sceneContractId

private fun NarrationResponse.matches(
    requestId: String,
    stateRevision: String,
    sceneContractId: String,
): Boolean = contractVersion == CONTRACT_VERSION &&
    this.requestId == requestId &&
    this.stateRevision == stateRevision &&
    this.sceneContractId == sceneContractId

private fun SceneResponse.matches(
    requestId: String,
    stateRevision: String,
    sceneContractId: String,
): Boolean = contractVersion == CONTRACT_VERSION &&
    this.requestId == requestId &&
    this.stateRevision == stateRevision &&
    this.sceneContractId == sceneContractId
