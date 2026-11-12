from fastapi import APIRouter, Request

from dungeon_master_ai.context.builder import build_interpretation_prompt
from dungeon_master_ai.providers.base import LlmProvider
from dungeon_master_ai.schemas.interpret import (
    InterpretationModelOutput,
    InterpretationRequest,
    InterpretationResponse,
)
from dungeon_master_ai.validation.retry import call_with_one_retry
from dungeon_master_ai.validation.validator import parse_interpretation

router = APIRouter()


def interpret_sentence(body: InterpretationRequest, provider: LlmProvider) -> InterpretationResponse:
    system, user = build_interpretation_prompt(body)
    output = call_with_one_retry(
        provider,
        system,
        user,
        InterpretationModelOutput.model_json_schema(),
        lambda raw: parse_interpretation(body, raw),
    )
    return InterpretationResponse(
        contractVersion=1,
        requestId=body.requestId,
        stateRevision=body.stateRevision,
        sceneContractId=body.sceneContract.id,
        status=output.status,
        candidateActionId=output.candidateActionId,
        candidateActionIds=list(output.candidateActionIds),
        parameters=dict(output.parameters),
        claims=list(output.claims),
    )


@router.post("/v1/interpret", response_model=InterpretationResponse)
def interpret(body: InterpretationRequest, request: Request) -> InterpretationResponse:
    return interpret_sentence(body, request.app.state.provider)
