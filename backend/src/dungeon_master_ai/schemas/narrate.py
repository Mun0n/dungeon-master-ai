import re
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

from dungeon_master_ai.schemas.common import (
    NARRATION_TEXT_MAX,
    AuthorizedContext,
    AuxiliaryKey,
    ConfirmedCheck,
    IdStr,
    SceneContract,
    TokenStr,
    assert_scene_alignment,
    bounded_text,
)
from dungeon_master_ai.schemas.effects import AppliedEffect


class ResolvedAction(BaseModel):
    """The resolution Kotlin already committed. This service only describes it."""

    model_config = ConfigDict(extra="forbid")

    actionId: IdStr | None = None
    outcome: Literal["SUCCEEDED", "FAILED", "REJECTED"]
    check: ConfirmedCheck | None = None
    appliedEffects: list[AppliedEffect] = Field(default_factory=list, max_length=32)

    @model_validator(mode="after")
    def matches_outcome(self) -> "ResolvedAction":
        if self.outcome == "REJECTED":
            if self.actionId is not None or self.check is not None or self.appliedEffects:
                raise ValueError("a rejection carries no action, check, or effects")
            return self
        if self.actionId is None:
            raise ValueError("a resolved action has an id")
        if self.outcome == "FAILED" and (self.check is None or self.check.success):
            raise ValueError("a failure has a failed check")
        if self.outcome == "SUCCEEDED" and self.check is not None and not self.check.success:
            raise ValueError("a success check succeeded")
        return self


class NarrationRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    contractVersion: Literal[1]
    requestId: TokenStr
    stateRevision: TokenStr
    sceneContract: SceneContract
    authorizedContext: AuthorizedContext
    resolvedAction: ResolvedAction
    lifeAfter: int = Field(ge=0, le=10)
    endingId: IdStr | None = None
    hora: str | None = None
    hecho: str | None = None
    allowedAuxiliaryKeys: list[AuxiliaryKey] = Field(default_factory=list, max_length=8)
    narrativeLanguage: Literal["es"] | None = None

    @field_validator("hora")
    @classmethod
    def clock_label(cls, value: str | None) -> str | None:
        if value is None:
            return None
        if re.fullmatch(r"\d{2}:\d{2}", value) is None:
            raise ValueError("hora")
        hour, minute = (int(part) for part in value.split(":"))
        if hour > 23 or minute > 59:
            raise ValueError("hora")
        return value

    @field_validator("hecho")
    @classmethod
    def confirmed_event(cls, value: str | None) -> str | None:
        if value is None:
            return None
        return bounded_text(value, 1, NARRATION_TEXT_MAX, "hecho length")

    @model_validator(mode="after")
    def aligned(self) -> "NarrationRequest":
        assert_scene_alignment(self.sceneContract, self.authorizedContext)
        action_id = self.resolvedAction.actionId
        # The contract is the one that authorized the resolution. The action does not
        # have to remain offered after that resolution moves the scene or ends the game.
        if action_id is not None and action_id not in self.sceneContract.allowedActionIds:
            raise ValueError("resolved action is outside the scene contract")
        if len(self.allowedAuxiliaryKeys) != len(set(self.allowedAuxiliaryKeys)):
            raise ValueError("duplicate auxiliary key")
        return self


class TextModelOutput(BaseModel):
    model_config = ConfigDict(extra="forbid")

    text: str
    auxiliary: dict[str, str] = Field(default_factory=dict)


class NarrationResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    contractVersion: Literal[1]
    requestId: TokenStr
    stateRevision: TokenStr
    sceneContractId: IdStr
    outcome: Literal["SUCCEEDED", "FAILED", "REJECTED"]
    text: str = Field(min_length=1, max_length=NARRATION_TEXT_MAX)
    auxiliary: dict[str, str]
