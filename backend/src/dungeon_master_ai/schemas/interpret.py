from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

from dungeon_master_ai.schemas.common import (
    PLAYER_TEXT_MAX,
    AuthorizedContext,
    IdStr,
    SceneContract,
    TokenStr,
    assert_scene_alignment,
    bounded_text,
)


class InterpretationRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    contractVersion: Literal[1]
    requestId: TokenStr
    stateRevision: TokenStr
    sceneContract: SceneContract
    authorizedContext: AuthorizedContext
    playerText: str
    narrativeLanguage: Literal["es"] | None = None

    @field_validator("playerText")
    @classmethod
    def player_text(cls, value: str) -> str:
        return bounded_text(value, 1, PLAYER_TEXT_MAX, "player text length")

    @model_validator(mode="after")
    def aligned(self) -> "InterpretationRequest":
        assert_scene_alignment(self.sceneContract, self.authorizedContext)
        return self


class ClaimSelection(BaseModel):
    model_config = ConfigDict(extra="forbid")

    claimId: IdStr
    optionId: IdStr


class InterpretationModelOutput(BaseModel):
    model_config = ConfigDict(extra="forbid")

    status: Literal[
        "RECOGNIZED",
        "AMBIGUOUS",
        "UNRECOGNIZED",
        "UNAVAILABLE",
        "REQUIREMENTS_REJECTED",
        "CONCLUSION",
    ]
    candidateActionId: IdStr | None = None
    candidateActionIds: list[IdStr] = Field(default_factory=list, max_length=8)
    parameters: dict[IdStr, IdStr] = Field(default_factory=dict)
    claims: list[ClaimSelection] = Field(default_factory=list, max_length=8)


class InterpretationResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    contractVersion: Literal[1]
    requestId: TokenStr
    stateRevision: TokenStr
    sceneContractId: IdStr
    status: Literal[
        "RECOGNIZED",
        "AMBIGUOUS",
        "UNRECOGNIZED",
        "UNAVAILABLE",
        "REQUIREMENTS_REJECTED",
        "CONCLUSION",
    ]
    candidateActionId: IdStr | None
    candidateActionIds: list[IdStr]
    parameters: dict[str, str]
    claims: list[ClaimSelection] = Field(default_factory=list)
