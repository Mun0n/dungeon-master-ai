package com.dungeonmasterai.data

import com.dungeonmasterai.domain.adventure.model.AdventureRules
import com.dungeonmasterai.domain.adventure.model.Effect
import com.dungeonmasterai.domain.adventure.narration.NarrationView
import com.dungeonmasterai.domain.dice.CheckResult
import java.net.URI
import java.net.URISyntaxException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal val backendJson: Json = Json {
    ignoreUnknownKeys = false
    isLenient = false
    explicitNulls = true
    encodeDefaults = true
    classDiscriminator = "type"
}

sealed interface BackendCall<out T> {
    data class Ready<T>(val body: T) : BackendCall<T>
    data class Failed(val code: String, val retryable: Boolean) : BackendCall<Nothing>
}

interface AdventureBackend {
    suspend fun interpret(request: InterpretationRequest): BackendCall<InterpretationResponse>
    suspend fun narrate(request: NarrationRequest): BackendCall<NarrationResponse>
    suspend fun scene(request: SceneRequest): BackendCall<SceneResponse>
}

@Serializable
data class InterpretationRequest(
    val contractVersion: Int,
    val requestId: String,
    val stateRevision: String,
    val sceneContract: SceneContractBody,
    val authorizedContext: AuthorizedContextBody,
    val playerText: String,
    val narrativeLanguage: String? = null,
)

@Serializable
data class InterpretationResponse(
    val contractVersion: Int,
    val requestId: String,
    val stateRevision: String,
    val sceneContractId: String,
    val status: String,
    val candidateActionId: String? = null,
    val candidateActionIds: List<String> = emptyList(),
    val parameters: Map<String, String> = emptyMap(),
    val claims: List<ClaimAnswerBody> = emptyList(),
)

@Serializable
data class NarrationRequest(
    val contractVersion: Int,
    val requestId: String,
    val stateRevision: String,
    val sceneContract: SceneContractBody,
    val authorizedContext: AuthorizedContextBody,
    val resolvedAction: ResolvedActionBody,
    val lifeAfter: Int,
    val endingId: String? = null,
    val hora: String? = null,
    val hecho: String? = null,
    val allowedAuxiliaryKeys: List<String> = emptyList(),
    val narrativeLanguage: String? = null,
)

@Serializable
data class NarrationResponse(
    val contractVersion: Int,
    val requestId: String,
    val stateRevision: String,
    val sceneContractId: String,
    val outcome: String,
    val text: String,
    val auxiliary: Map<String, String> = emptyMap(),
)

@Serializable
data class SceneRequest(
    val contractVersion: Int,
    val requestId: String,
    val stateRevision: String,
    val generationRequired: Boolean,
    val sceneContract: SceneContractBody,
    val authorizedContext: AuthorizedContextBody,
    val allowedAuxiliaryKeys: List<String> = emptyList(),
    val narrativeLanguage: String? = null,
)

@Serializable
data class SceneResponse(
    val contractVersion: Int,
    val requestId: String,
    val stateRevision: String,
    val sceneContractId: String,
    val text: String,
    val auxiliary: Map<String, String> = emptyMap(),
)

@Serializable
data class SceneContractBody(
    val id: String,
    val locationId: String,
    val purpose: String,
    val allowedActionIds: List<String>,
    val presentNpcIds: List<String>,
    val revealableInformation: List<String>,
    val forbiddenInformation: List<String>,
)

@Serializable
data class AuthorizedContextBody(
    val locationName: String,
    val presentNpcs: List<NpcBody>,
    val availableActions: List<ActionBody>,
    val unavailableActions: List<String>,
    val requirementBlockedActions: List<String>,
    val allowedParameterKeys: List<String> = emptyList(),
    val parameterValueIds: List<String> = emptyList(),
    val recentInteractions: List<InteractionBody>,
    val narrativeSummary: String,
    val availableConclusions: List<ConclusionBody> = emptyList(),
    val revealedLines: List<String> = emptyList(),
)

@Serializable
data class NpcBody(
    val id: String,
    val name: String,
    val state: String? = null,
)

@Serializable
data class ClaimAnswerBody(
    val claimId: String,
    val optionId: String,
)

@Serializable
data class ConclusionOptionBody(
    val id: String,
    val gloss: String,
)

@Serializable
data class ConclusionClaimBody(
    val id: String,
    val prompt: String,
    val options: List<ConclusionOptionBody>,
)

@Serializable
data class ConclusionBody(
    val id: String,
    val claims: List<ConclusionClaimBody>,
)

@Serializable
data class ActionBody(
    val id: String,
    val label: String,
)

@Serializable
data class InteractionBody(
    val speaker: String,
    val text: String,
)

@Serializable
data class ResolvedActionBody(
    val actionId: String? = null,
    val outcome: String,
    val check: ConfirmedCheckBody? = null,
    val appliedEffects: List<Effect> = emptyList(),
)

@Serializable
data class ConfirmedCheckBody(
    val attribute: String,
    val roll: Int,
    val modifier: Int,
    val total: Int,
    val difficulty: Int,
    val success: Boolean,
)

@Serializable
data class ErrorEnvelope(
    val error: ErrorBody,
)

@Serializable
data class ErrorBody(
    val code: String,
    val retryable: Boolean,
    val attempt: Int,
)

internal fun requireAdventureBackendUrl(url: String, allowCleartext: Boolean) {
    val uri = try {
        URI(url)
    } catch (error: URISyntaxException) {
        throw IllegalArgumentException("Adventure backend URL is invalid.")
    }
    val scheme = uri.scheme?.lowercase()
    if (scheme == "http" && !allowCleartext) {
        throw IllegalArgumentException("Cleartext adventure backend URLs are debug-only.")
    }
    if (scheme != "https" && scheme != "http") {
        throw IllegalArgumentException("Adventure backend URL must use https.")
    }
    if (!uri.rawUserInfo.isNullOrEmpty()) {
        throw IllegalArgumentException("Adventure backend URL cannot include credentials.")
    }
    if (uri.rawQuery != null || uri.rawFragment != null) {
        throw IllegalArgumentException("Adventure backend URL cannot include a query or fragment.")
    }
    if (uri.host.isNullOrBlank()) {
        throw IllegalArgumentException("Adventure backend URL needs a host.")
    }
    val path = uri.path.orEmpty()
    if (path.isNotEmpty() && path != "/") {
        throw IllegalArgumentException("Adventure backend URL cannot include a path.")
    }
}

internal fun NarrationView.toContractBody(
    forbiddenInformation: List<String> = contract.forbiddenInformation,
): SceneContractBody = SceneContractBody(
    id = contract.id,
    locationId = contract.locationId,
    purpose = contract.purpose,
    allowedActionIds = contract.allowedActionIds,
    presentNpcIds = contract.presentNpcIds,
    revealableInformation = contract.revealableInformation,
    forbiddenInformation = forbiddenInformation,
)

internal fun NarrationView.toContextBody(
    revealedLines: List<String> = emptyList(),
): AuthorizedContextBody = AuthorizedContextBody(
    locationName = locationName,
    presentNpcs = presentNpcs.map { npc -> NpcBody(id = npc.id, name = npc.name, state = npc.state) },
    availableActions = availableActions.map { action -> ActionBody(id = action.id, label = action.label) },
    unavailableActions = unavailableActionIds,
    requirementBlockedActions = blockedActionIds,
    recentInteractions = recentLines.map { line -> InteractionBody(speaker = line.speaker, text = line.text) },
    narrativeSummary = narrativeSummary,
    availableConclusions = availableConclusions.map { conclusion ->
        ConclusionBody(
            id = conclusion.id,
            claims = conclusion.claims.map { claim ->
                ConclusionClaimBody(
                    id = claim.id,
                    prompt = claim.prompt,
                    options = claim.options.map { option ->
                        ConclusionOptionBody(id = option.id, gloss = option.gloss)
                    },
                )
            },
        )
    },
    revealedLines = revealedLines,
)

internal fun confirmedCheck(attribute: String?, check: CheckResult?): ConfirmedCheckBody? {
    if (check == null) return null
    val name = attribute ?: return null
    return ConfirmedCheckBody(
        attribute = name,
        roll = check.roll,
        modifier = check.modifier,
        total = check.total,
        difficulty = check.difficulty,
        success = check.success,
    )
}

internal const val CONTRACT_VERSION: Int = 1

internal fun fitsPlayerText(text: String): Boolean =
    text.isNotEmpty() && text.length <= AdventureRules.PLAYER_TEXT_MAX
