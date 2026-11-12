import json

from pydantic import ValidationError

from dungeon_master_ai.errors import CONTRACT_VIOLATION, INVALID_MODEL_OUTPUT, ModelOutputError
from dungeon_master_ai.schemas.common import (
    AUXILIARY_MAX,
    AUXILIARY_VALUE_MAX,
    NARRATION_TEXT_MAX,
    SCENE_TEXT_MAX,
    SUMMARY_MAX,
    contains_forbidden,
)
from dungeon_master_ai.schemas.interpret import InterpretationModelOutput, InterpretationRequest
from dungeon_master_ai.schemas.narrate import NarrationRequest, TextModelOutput
from dungeon_master_ai.schemas.scene import SceneRequest
from dungeon_master_ai.validation.redaction import contains_player_technical_token, redact_summary


def parse_interpretation(body: InterpretationRequest, raw: str) -> InterpretationModelOutput:
    output = _parse(raw, InterpretationModelOutput)
    _check_interpretation(body, output)
    return output


def parse_narration(body: NarrationRequest, raw: str) -> TextModelOutput:
    return _parse_prose(
        raw,
        limit=NARRATION_TEXT_MAX,
        allowed_keys=set(body.allowedAuxiliaryKeys),
        forbidden=body.sceneContract.forbiddenInformation,
    )


def parse_scene(body: SceneRequest, raw: str) -> TextModelOutput:
    return _parse_prose(
        raw,
        limit=SCENE_TEXT_MAX,
        allowed_keys=set(body.allowedAuxiliaryKeys),
        forbidden=body.sceneContract.forbiddenInformation,
    )


def _check_interpretation(body: InterpretationRequest, output: InterpretationModelOutput) -> None:
    context = body.authorizedContext
    available = {action.id for action in context.availableActions}
    unavailable = set(context.unavailableActions)
    blocked = set(context.requirementBlockedActions)
    allowed_keys = set(context.allowedParameterKeys)
    allowed_values = set(context.parameterValueIds)
    one = output.candidateActionId
    many = output.candidateActionIds
    parameters = output.parameters
    claims = output.claims
    distinct = len(many) == len(set(many))

    if output.status == "CONCLUSION":
        accepted = _conclusion_matches(context.availableConclusions, one, many, parameters, claims)
    elif claims:
        accepted = False
    elif output.status == "RECOGNIZED":
        accepted = (
            one in available
            and not many
            and set(parameters) <= allowed_keys
            and set(parameters.values()) <= allowed_values
        )
    elif output.status == "AMBIGUOUS":
        accepted = (
            one is None
            and len(many) >= 2
            and distinct
            and set(many) <= available
            and not parameters
        )
    elif output.status == "UNRECOGNIZED":
        accepted = one is None and not many and not parameters
    elif output.status == "UNAVAILABLE":
        accepted = one in unavailable and not many and not parameters
    else:
        accepted = one in blocked and not many and not parameters

    if not accepted:
        if output.status == "CONCLUSION" or claims:
            offered_ids = [
                claim.id
                for conclusion in context.availableConclusions
                for claim in conclusion.claims
            ]
            listed = ", ".join(offered_ids) if offered_ids else "the offered claims"
            raise ModelOutputError(
                CONTRACT_VIOLATION,
                [
                    "CONCLUSION needs a null candidateActionId, empty candidateActionIds, "
                    "empty parameters, and exactly one claims entry for each of these claim ids: "
                    f"{listed}. Each optionId must be one of that claim's options."
                ],
            )
        raise ModelOutputError(
            CONTRACT_VIOLATION,
            ["interpretation does not match the authorized actions"],
        )


def _conclusion_matches(offered, one, many, parameters, claims) -> bool:
    if one is not None or many or parameters or len(offered) != 1:
        return False
    expected = {claim.id: {option.id for option in claim.options} for claim in offered[0].claims}
    seen = [item.claimId for item in claims]
    if len(seen) != len(set(seen)) or set(seen) != set(expected):
        return False
    return all(item.optionId in expected[item.claimId] for item in claims)


def _parse_prose(
    raw: str,
    *,
    limit: int,
    allowed_keys: set[str],
    forbidden: list[str],
) -> TextModelOutput:
    output = _parse(raw, TextModelOutput)
    text = output.text.strip()
    if not text or len(text) > limit or len(output.auxiliary) > AUXILIARY_MAX:
        raise ModelOutputError(INVALID_MODEL_OUTPUT, ["response failed schema validation"])

    cleaned: dict[str, str] = {}
    for key, value in output.auxiliary.items():
        if key not in allowed_keys:
            raise ModelOutputError(CONTRACT_VIOLATION, ["auxiliary field is not allowed"])
        item = value.strip()
        if key == "narrativeSummary" and not item:
            continue
        cap = SUMMARY_MAX if key == "narrativeSummary" else AUXILIARY_VALUE_MAX
        if not item or len(item) > cap:
            raise ModelOutputError(INVALID_MODEL_OUTPUT, ["response failed schema validation"])
        cleaned[key] = item

    summary = cleaned.get("narrativeSummary")
    if summary is not None:
        redacted = redact_summary(summary, forbidden)
        if redacted:
            cleaned["narrativeSummary"] = redacted
        else:
            del cleaned["narrativeSummary"]

    if contains_forbidden([text], forbidden) or contains_player_technical_token(text):
        raise ModelOutputError(
            CONTRACT_VIOLATION,
            ["response contains forbidden information"],
        )
    output.text = text
    output.auxiliary = cleaned
    return output


def _parse(raw: str, model: type[InterpretationModelOutput] | type[TextModelOutput]):
    data = _json_object(raw)
    try:
        return model.model_validate(data)
    except ValidationError as error:
        raise ModelOutputError(
            INVALID_MODEL_OUTPUT,
            ["response failed schema validation"],
        ) from None


def _json_object(raw: str) -> dict:
    text = raw.strip()
    if text.startswith("```"):
        lines = text.splitlines()
        opening = lines[0].strip() if lines else ""
        closing = lines[-1].strip() if lines else ""
        if len(lines) < 3 or opening not in {"```", "```json"} or closing != "```":
            raise ModelOutputError(INVALID_MODEL_OUTPUT, ["response is not a json object"])
        text = "\n".join(lines[1:-1]).strip()
    try:
        value = json.loads(text)
    except json.JSONDecodeError as error:
        raise ModelOutputError(INVALID_MODEL_OUTPUT, ["response is not a json object"]) from None
    if not isinstance(value, dict):
        raise ModelOutputError(INVALID_MODEL_OUTPUT, ["response is not a json object"])
    return value
