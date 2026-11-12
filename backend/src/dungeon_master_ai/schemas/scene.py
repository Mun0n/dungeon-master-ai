from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator

from dungeon_master_ai.schemas.common import (
    SCENE_TEXT_MAX,
    AuthorizedContext,
    AuxiliaryKey,
    IdStr,
    SceneContract,
    TokenStr,
    assert_scene_alignment,
)


class SceneRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    contractVersion: Literal[1]
    requestId: TokenStr
    stateRevision: TokenStr
    generationRequired: bool
    sceneContract: SceneContract
    authorizedContext: AuthorizedContext
    allowedAuxiliaryKeys: list[AuxiliaryKey] = Field(default_factory=list, max_length=8)
    narrativeLanguage: Literal["es"] | None = None

    @model_validator(mode="after")
    def aligned(self) -> "SceneRequest":
        assert_scene_alignment(self.sceneContract, self.authorizedContext)
        if len(self.allowedAuxiliaryKeys) != len(set(self.allowedAuxiliaryKeys)):
            raise ValueError("duplicate auxiliary key")
        return self


class SceneResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    contractVersion: Literal[1]
    requestId: TokenStr
    stateRevision: TokenStr
    sceneContractId: IdStr
    text: str = Field(min_length=1, max_length=SCENE_TEXT_MAX)
    auxiliary: dict[str, str]
