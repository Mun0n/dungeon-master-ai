from fastapi import APIRouter, Request
from pydantic import BaseModel, ConfigDict

from dungeon_master_ai.settings import Settings

router = APIRouter()


class HealthResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    status: str
    provider: str
    model: str


@router.get("/health", response_model=HealthResponse)
def health(request: Request) -> HealthResponse:
    settings: Settings = request.app.state.settings
    return HealthResponse(status="ok", provider=settings.llm_provider, model=settings.model_name)
