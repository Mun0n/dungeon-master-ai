from fastapi import APIRouter, Request

from dungeon_master_ai.context.builder import build_narration_prompt
from dungeon_master_ai.providers.base import LlmProvider
from dungeon_master_ai.schemas.narrate import NarrationRequest, NarrationResponse, TextModelOutput
from dungeon_master_ai.validation.retry import call_with_one_retry
from dungeon_master_ai.validation.validator import parse_narration

router = APIRouter()


def narrate_resolution(body: NarrationRequest, provider: LlmProvider) -> NarrationResponse:
    system, user = build_narration_prompt(body)
    output = call_with_one_retry(
        provider,
        system,
        user,
        TextModelOutput.model_json_schema(),
        lambda raw: parse_narration(body, raw),
    )
    return NarrationResponse(
        contractVersion=1,
        requestId=body.requestId,
        stateRevision=body.stateRevision,
        sceneContractId=body.sceneContract.id,
        outcome=body.resolvedAction.outcome,
        text=output.text,
        auxiliary=output.auxiliary,
    )


@router.post("/v1/narrate", response_model=NarrationResponse)
def narrate(body: NarrationRequest, request: Request) -> NarrationResponse:
    return narrate_resolution(body, request.app.state.provider)
