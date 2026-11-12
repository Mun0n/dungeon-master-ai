package com.dungeonmasterai.domain.adventure.narration

import com.dungeonmasterai.domain.adventure.engine.ActionAccess
import com.dungeonmasterai.domain.adventure.engine.AdventureEngine
import com.dungeonmasterai.domain.adventure.engine.offersConclusion
import com.dungeonmasterai.domain.adventure.model.AdventureRules
import com.dungeonmasterai.domain.adventure.model.SceneContract
import com.dungeonmasterai.domain.adventure.state.AdventureState

const val DISPLAY_NAME_MAX: Int = 120

data class RecentLine(
    val speaker: String,
    val text: String,
)

data class PresentNpc(
    val id: String,
    val name: String,
    val state: String?,
)

data class OfferedAction(
    val id: String,
    val label: String,
)

data class OfferedOption(
    val id: String,
    val gloss: String,
)

data class OfferedClaim(
    val id: String,
    val prompt: String,
    val options: List<OfferedOption>,
)

data class OfferedConclusion(
    val id: String,
    val claims: List<OfferedClaim>,
)

/**
 * The fields a narrator is allowed to see for one scene.
 * Evidence, endings, and unapplied effects have no place here.
 * A conclusion offer hides the accepted values and the contradiction pairs.
 */
data class NarrationView(
    val contract: SceneContract,
    val locationName: String,
    val presentNpcs: List<PresentNpc>,
    val availableActions: List<OfferedAction>,
    val unavailableActionIds: List<String>,
    val blockedActionIds: List<String>,
    val recentLines: List<RecentLine>,
    val narrativeSummary: String,
    val availableConclusions: List<OfferedConclusion> = emptyList(),
)

/**
 * Returns the single scene contract for the current location, or null when the
 * location has no contract or has more than one.
 */
fun AdventureEngine.narrationView(
    state: AdventureState,
    recentLines: List<RecentLine>,
    inesLine: String? = null,
): NarrationView? {
    require(state.adventureId == adventure.id) { "Wrong adventure: ${state.adventureId}." }
    val contract = adventure.sceneContracts
        .filter { it.locationId == state.locationId }
        .singleOrNull()
        ?: return null
    return narrationView(state, contract, recentLines, inesLine)
}

/**
 * Builds the view for a contract chosen before the action resolved.
 * A later move does not swap this contract for the destination.
 */
fun AdventureEngine.narrationView(
    state: AdventureState,
    contractId: String,
    recentLines: List<RecentLine>,
    inesLine: String? = null,
): NarrationView? {
    require(state.adventureId == adventure.id) { "Wrong adventure: ${state.adventureId}." }
    val contract = adventure.sceneContracts.firstOrNull { it.id == contractId } ?: return null
    return narrationView(state, contract, recentLines, inesLine)
}

private fun AdventureEngine.narrationView(
    state: AdventureState,
    contract: SceneContract,
    recentLines: List<RecentLine>,
    inesLine: String?,
): NarrationView? {
    val location = adventure.locations.firstOrNull { it.id == contract.locationId } ?: return null
    if (location.name.isBlank() || location.name.length > DISPLAY_NAME_MAX) return null
    if (contract.purpose.isBlank() || contract.purpose.length > AdventureRules.PURPOSE_MAX) return null
    if (state.narrativeSummary.length > AdventureRules.SUMMARY_MAX) return null
    val npcs = contract.presentNpcIds.map { npcId ->
        val npc = adventure.npcs.firstOrNull { it.id == npcId } ?: return null
        if (npc.name.isBlank() || npc.name.length > DISPLAY_NAME_MAX) return null
        PresentNpc(id = npc.id, name = npc.name, state = state.npcStates[npc.id])
    }
    val available = mutableListOf<OfferedAction>()
    val blocked = mutableListOf<String>()
    val unavailable = mutableListOf<String>()
    contract.allowedActionIds.forEach { actionId ->
        if (actionId.length > DISPLAY_NAME_MAX) return null
        when (access(state, actionId)) {
            ActionAccess.AVAILABLE -> available += OfferedAction(id = actionId, label = actionId)
            ActionAccess.REQUIREMENTS_BLOCKED -> blocked += actionId
            ActionAccess.UNAVAILABLE, ActionAccess.UNKNOWN -> unavailable += actionId
        }
    }
    val secrets = activeForbiddenPhrases(
        documentPhrases = contract.forbiddenInformation,
        evidenceIds = state.evidenceIds,
        truthKnown = state.flags["truth_known"] == true,
        inesLine = inesLine,
        clueIds = state.clueIds,
    )
    return NarrationView(
        contract = contract,
        locationName = location.name,
        presentNpcs = npcs,
        availableActions = available,
        unavailableActionIds = unavailable,
        blockedActionIds = blocked,
        recentLines = recentLines
            .filterNot { line -> lineIsHidden(line.text, secrets) }
            .takeLast(AdventureRules.RECENT_LINES),
        narrativeSummary = redactSummary(state.narrativeSummary, secrets),
        availableConclusions = offeredConclusions(state),
    )
}

private fun lineIsHidden(text: String, secrets: List<String>): Boolean =
    containsListedPhrase(text, secrets) || containsPlayerTechnicalToken(text)

private fun AdventureEngine.offeredConclusions(state: AdventureState): List<OfferedConclusion> =
    adventure.deductions.mapNotNull { deduction ->
        if (!deduction.offersConclusion(state)) return@mapNotNull null
        OfferedConclusion(
            id = deduction.id,
            claims = deduction.claims.map { claim ->
                OfferedClaim(
                    id = claim.id,
                    prompt = claim.prompt,
                    options = claim.options.map { option ->
                        OfferedOption(id = option.id, gloss = option.gloss)
                    },
                )
            },
        )
    }
