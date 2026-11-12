import json

from dungeon_master_ai.errors import CONTEXT_REJECTED, ApiError
from dungeon_master_ai.schemas.common import contains_forbidden
from dungeon_master_ai.schemas.interpret import InterpretationRequest
from dungeon_master_ai.schemas.narrate import NarrationRequest
from dungeon_master_ai.schemas.scene import SceneRequest
from dungeon_master_ai.validation.redaction import (
    ATTRIBUTE_WORDS,
    ENDING_TITLES,
    OUTCOME_WORDS,
    redact_summary,
)

SPANISH_INTERPRET = (
    "The narrative language is Spanish (es). "
    "The player sentence is Spanish. "
    "Map it to the action ids in the user message. "
    "Do not translate those ids."
)

SPANISH_VOICE = (
    "Use the second person and the present tense. "
    "The mood is mystery and dread. "
    "You may add sensory and emotional detail that rests on the revealed facts. "
    "You may not invent a canonical fact, a relevant character, a culprit, a proof, "
    "a consequence, or an event unless the revealed facts already authorize it. "
    "A rumor stays a rumor. "
    "Do not explain the puzzle and do not tell the player which action to take next."
)

SPANISH_NARRATE = (
    "The narrative language is Spanish (es). "
    "Write the narration in natural Spanish. "
    f"{SPANISH_VOICE} "
    "Keep proper names as given. "
    "Do not copy identifiers, flag names, action ids, claim ids, option ids, or effect names. "
    "Stay inside the confirmed facts. "
    "Do not copy hora, life, the die, or the difficulty into the prose. "
    "Do not write a clock time. "
    "Describe what the character does or experiences."
)

SPANISH_SCENE = (
    "The narrative language is Spanish (es). "
    "Write the scene in natural Spanish. "
    f"{SPANISH_VOICE} "
    "Keep proper names as given. "
    "Do not copy identifiers, flag names, action ids, claim ids, option ids, or effect names. "
    "Use only the authorized scene fields."
)


def _with_spanish(system: str, language: str | None, instruction: str) -> str:
    if language is None:
        return system
    return f"{system} {instruction}"


INTERPRET_SYSTEM = (
    "Interpret one player sentence for a gamebook. "
    "Reply with one JSON object and no other text. "
    "The keys are status, candidateActionId, candidateActionIds, parameters, and claims. "
    "status is RECOGNIZED, AMBIGUOUS, UNRECOGNIZED, UNAVAILABLE, REQUIREMENTS_REJECTED, or CONCLUSION. "
    "RECOGNIZED uses one id from availableActions, an empty candidateActionIds, and empty claims. "
    "AMBIGUOUS uses at least two ids from availableActions, a null candidateActionId, and empty claims. "
    "UNRECOGNIZED uses a null candidateActionId, an empty candidateActionIds, and empty claims. "
    "UNAVAILABLE uses one id from unavailableActions and empty claims. "
    "REQUIREMENTS_REJECTED uses one id from requirementBlockedActions and empty claims. "
    "parameters is empty unless status is RECOGNIZED, and then every key is in "
    "allowedParameterKeys and every value is in parameterValueIds. "
    "CONCLUSION is allowed only when availableConclusions is not empty. "
    "It uses a null candidateActionId, an empty candidateActionIds, empty parameters, "
    "and one claims entry for every claim of that offered conclusion. "
    "Each entry has claimId and optionId. optionId is one id from that claim's options. "
    "Read the whole sentence. Negation, contradiction, and a wrong person change the meaning. "
    "Do not choose an option because a name or a keyword appears. "
    "A sentence that says someone did not commit the murders does not select the option that blames that person. "
    "Use the unknown option when the sentence does not assert that claim. "
    "Do not return an action and a conclusion together. "
    "Use only ids present in the user message. Do not add keys."
)

NARRATION_USE = (
    "Use hora, life, and the check only as confirmed context. "
    "Do not copy them into the prose. "
    "Do not write a clock time. "
    "Describe what the character does or experiences. "
    "Finding a path is not entering. "
    "Do not say the character arrives inside, and do not describe rooms or shelves, "
    "unless hecho says they go in or location is already that place. "
    "Do not copy a sentence from the recent lines. "
    "location is the only confirmed place."
)

NARRATE_SYSTEM = (
    "Narrate a gamebook result that is already resolved. "
    "Reply with one JSON object and no other text. "
    "The keys are text and auxiliary. "
    "Describe the outcome in the user message. "
    "completed means the action succeeded. not completed means it failed. rejected means it did not happen. "
    "A failed or rejected outcome stays unsuccessful. "
    "location is where the player is now. It is the only confirmed place. "
    "A place named only in the summary or in an older line is not the current place. "
    "Finding a path or an entrance is not the same as going there. "
    "Narrate entry only when hecho says the character goes in, or when location is already that place. "
    "A discovery that only finds the way leaves the character outside, where location says they are. "
    "Do not describe the interior of a place they have only found. "
    "hora, when present, is the confirmed time. "
    "life is the confirmed life after that resolution. "
    "A check, when present, is the confirmed roll: the die, the attribute, the modifier, the total, the difficulty, and whether it succeeded. "
    "Those values are context. The interface already shows them. "
    "Do not recite the clock, the life total, the die, the attribute, or the difficulty. "
    "Do not copy those numbers into the sentence. "
    "For example, do not write that it is 22:10, that the character has 10 life points, or that they pass an Agility test. "
    "Describe what the character does or experiences because of that result. "
    "Do not narrate an event as happening now, or as about to happen, when the revealed facts place it at a later hour. "
    "A comment or a rumor about that later hour stays a comment or a rumor. "
    "hecho, when present, is the confirmed prose of this resolution. "
    "Narrate that event in the current location. "
    "Do not replace this resolution with a retelling of the summary. "
    "Do not repeat a sensory image that recentInteractions already used, unless this resolution needs it. "
    "That is a matter of freshness, not a list of forbidden words. "
    "Use the revealed lines. Do not invent a roll, a total, or an effect. "
    "ending, when present, is the confirmed ending title. "
    "Do not invent a death or an ending that is absent, and do not change them. "
    "auxiliary may contain only keys listed in allowedAuxiliaryKeys. "
    "Do not add keys."
)

SCENE_SYSTEM = (
    "Write the prose for one authored scene. "
    "Reply with one JSON object and no other text. "
    "The keys are text and auxiliary. "
    "Use only the purpose, the present characters, and the revealable information "
    "in the user message. "
    "auxiliary may contain only keys listed in allowedAuxiliaryKeys. "
    "Do not add keys."
)


def build_interpretation_prompt(body: InterpretationRequest) -> tuple[str, str]:
    context = body.authorizedContext
    view = {
        "purpose": body.sceneContract.purpose,
        "locationId": body.sceneContract.locationId,
        "locationName": context.locationName,
        "presentNpcs": [npc.model_dump(mode="json") for npc in context.presentNpcs],
        "availableActions": [action.model_dump(mode="json") for action in context.availableActions],
        "unavailableActions": list(context.unavailableActions),
        "requirementBlockedActions": list(context.requirementBlockedActions),
        "allowedParameterKeys": list(context.allowedParameterKeys),
        "parameterValueIds": list(context.parameterValueIds),
        "revealableInformation": list(body.sceneContract.revealableInformation),
        "narrativeSummary": redact_summary(
            context.narrativeSummary,
            body.sceneContract.forbiddenInformation,
        ),
        "recentInteractions": [
            item.model_dump(mode="json") for item in context.recentInteractions
        ],
        "availableConclusions": [
            item.model_dump(mode="json") for item in context.availableConclusions
        ],
        "playerText": body.playerText,
    }
    system = _with_spanish(INTERPRET_SYSTEM, body.narrativeLanguage, SPANISH_INTERPRET)
    return system, _user_message(view, body.sceneContract.forbiddenInformation)


def build_narration_prompt(body: NarrationRequest) -> tuple[str, str]:
    context = body.authorizedContext
    forbidden = body.sceneContract.forbiddenInformation
    background = _prose_view(
        body.sceneContract.purpose,
        body.sceneContract.revealableInformation,
        context,
        forbidden,
    )
    view = {"location": background.pop("location")}
    if body.hora:
        view["hora"] = body.hora
    if body.hecho:
        view["hecho"] = body.hecho
    view["outcome"] = OUTCOME_WORDS[body.resolvedAction.outcome]
    check = _check_words(body.resolvedAction.check)
    if check is not None:
        view["check"] = check
    view["life"] = body.lifeAfter
    view.update(background)
    view["allowedAuxiliaryKeys"] = list(body.allowedAuxiliaryKeys)
    if body.endingId is not None and body.endingId in ENDING_TITLES:
        view["ending"] = ENDING_TITLES[body.endingId]
    system = _with_spanish(NARRATE_SYSTEM, body.narrativeLanguage, SPANISH_NARRATE)
    user = _user_message(view, forbidden)
    return system, f"{user} {NARRATION_USE}"


def build_scene_prompt(body: SceneRequest) -> tuple[str, str]:
    context = body.authorizedContext
    forbidden = body.sceneContract.forbiddenInformation
    view = _prose_view(body.sceneContract.purpose, body.sceneContract.revealableInformation, context, forbidden)
    view["allowedAuxiliaryKeys"] = list(body.allowedAuxiliaryKeys)
    system = _with_spanish(SCENE_SYSTEM, body.narrativeLanguage, SPANISH_SCENE)
    return system, _user_message(view, forbidden)


def _prose_view(purpose: str, revealable: list[str], context, forbidden: list[str]) -> dict:
    summary = redact_summary(context.narrativeSummary, forbidden)
    view = {
        "purpose": purpose,
        "location": context.locationName,
        "presentCharacters": [npc.name for npc in context.presentNpcs],
        "revealableInformation": list(revealable),
        "revealedLines": list(context.revealedLines),
        "recentInteractions": [
            item.model_dump(mode="json") for item in context.recentInteractions
        ],
    }
    if summary:
        view["narrativeSummary"] = summary
    return view


def _check_words(check) -> dict | None:
    if check is None:
        return None
    return {
        "dado": check.roll,
        "atributo": ATTRIBUTE_WORDS[check.attribute],
        "modificador": check.modifier,
        "total": check.total,
        "dificultad": check.difficulty,
        "resultado": "Supera la prueba" if check.success else "No supera la prueba",
    }


def _user_message(view: dict, forbidden: list[str]) -> str:
    """Serialize the authorized view. The forbidden list is not a field of that view.

    Values are checked before serialization. JSON escaping would hide a quote or
    a backslash from a search of the finished text, and the model would still see it.
    """

    if contains_forbidden(_text_values(view), forbidden):
        raise ApiError(CONTEXT_REJECTED, 422, False, 1)
    message = json.dumps(view, ensure_ascii=False, separators=(",", ":"))
    if contains_forbidden([message], forbidden):
        raise ApiError(CONTEXT_REJECTED, 422, False, 1)
    return message


def _text_values(value: object) -> list[str]:
    if isinstance(value, str):
        return [value]
    if isinstance(value, dict):
        return [item for nested in value.values() for item in _text_values(nested)]
    if isinstance(value, list):
        return [item for nested in value for item in _text_values(nested)]
    return []
