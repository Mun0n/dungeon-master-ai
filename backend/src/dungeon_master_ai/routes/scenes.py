from fastapi import APIRouter, Request

from dungeon_master_ai.context.builder import build_scene_prompt
from dungeon_master_ai.errors import GENERATION_NOT_REQUIRED, ApiError
from dungeon_master_ai.providers.base import LlmProvider
from dungeon_master_ai.schemas.narrate import TextModelOutput
from dungeon_master_ai.schemas.scene import SceneRequest, SceneResponse
from dungeon_master_ai.validation.retry import call_with_one_retry
from dungeon_master_ai.validation.validator import parse_scene

router = APIRouter()


def generate_scene(body: SceneRequest, provider: LlmProvider) -> SceneResponse:
    if not body.generationRequired:
        raise ApiError(GENERATION_NOT_REQUIRED, 422, False, 1)
    system, user = build_scene_prompt(body)
    output = call_with_one_retry(
        provider,
        system,
        user,
        TextModelOutput.model_json_schema(),
        lambda raw: parse_scene(body, raw),
    )
    return SceneResponse(
        contractVersion=1,
        requestId=body.requestId,
        stateRevision=body.stateRevision,
        sceneContractId=body.sceneContract.id,
        text=output.text,
        auxiliary=output.auxiliary,
    )


@router.post("/v1/scenes", response_model=SceneResponse)
def scenes(body: SceneRequest, request: Request) -> SceneResponse:
    return generate_scene(body, request.app.state.provider)
