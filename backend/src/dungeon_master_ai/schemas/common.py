from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

PLAYER_TEXT_MAX = 500
NARRATION_TEXT_MAX = 1_200
SCENE_TEXT_MAX = 2_000
SUMMARY_MAX = 3_000
AUXILIARY_VALUE_MAX = NARRATION_TEXT_MAX
AUXILIARY_MAX = 8
FORBIDDEN_MIN = 8
DISPLAY_MAX = 120
PURPOSE_MAX = 500
CONTRACT_VERSION = 1

IdStr = Annotated[str, Field(pattern=r"^[a-z][a-z0-9_]*$", min_length=1, max_length=64)]
TokenStr = Annotated[str, Field(pattern=r"^[A-Za-z0-9_-]{1,64}$")]
AuxiliaryKey = Annotated[str, Field(pattern=r"^[A-Za-z][A-Za-z0-9_]{0,40}$")]


class SceneContract(BaseModel):
    model_config = ConfigDict(extra="forbid")

    id: IdStr
    locationId: IdStr
    purpose: str
    allowedActionIds: list[IdStr] = Field(default_factory=list, max_length=32)
    presentNpcIds: list[IdStr] = Field(default_factory=list, max_length=12)
    revealableInformation: list[str] = Field(default_factory=list, max_length=16)
    forbiddenInformation: list[str] = Field(default_factory=list, max_length=32)

    @field_validator("purpose")
    @classmethod
    def purpose_text(cls, value: str) -> str:
        return bounded_text(value, 1, PURPOSE_MAX, "purpose length")

    @field_validator("allowedActionIds", "presentNpcIds")
    @classmethod
    def unique_ids(cls, value: list[str]) -> list[str]:
        if len(value) != len(set(value)):
            raise ValueError("duplicate id")
        return value

    @field_validator("revealableInformation")
    @classmethod
    def revealable(cls, value: list[str]) -> list[str]:
        return [bounded_text(item, 1, PURPOSE_MAX, "revealable length") for item in value]

    @field_validator("forbiddenInformation")
    @classmethod
    def forbidden(cls, value: list[str]) -> list[str]:
        return [bounded_text(item, FORBIDDEN_MIN, PURPOSE_MAX, "forbidden length") for item in value]


class NpcView(BaseModel):
    model_config = ConfigDict(extra="forbid")

    id: IdStr
    name: str
    state: IdStr | None = None

    @field_validator("name")
    @classmethod
    def name_text(cls, value: str) -> str:
        return bounded_text(value, 1, DISPLAY_MAX, "name length")


class ActionView(BaseModel):
    model_config = ConfigDict(extra="forbid")

    id: IdStr
    label: str

    @field_validator("label")
    @classmethod
    def label_text(cls, value: str) -> str:
        return bounded_text(value, 1, DISPLAY_MAX, "label length")


class ClaimOptionView(BaseModel):
    model_config = ConfigDict(extra="forbid")

    id: IdStr
    gloss: str

    @field_validator("gloss")
    @classmethod
    def gloss_text(cls, value: str) -> str:
        return bounded_text(value, 1, PURPOSE_MAX, "gloss length")


class ClaimView(BaseModel):
    model_config = ConfigDict(extra="forbid")

    id: IdStr
    prompt: str
    options: list[ClaimOptionView] = Field(min_length=2, max_length=8)

    @field_validator("prompt")
    @classmethod
    def prompt_text(cls, value: str) -> str:
        return bounded_text(value, 1, PURPOSE_MAX, "claim prompt length")

    @model_validator(mode="after")
    def unique_options(self) -> "ClaimView":
        option_ids = [option.id for option in self.options]
        if len(option_ids) != len(set(option_ids)):
            raise ValueError("duplicate id")
        return self


class ConclusionView(BaseModel):
    """Claims the model may fill. Accepted values and contradictions are absent."""

    model_config = ConfigDict(extra="forbid")

    id: IdStr
    claims: list[ClaimView] = Field(min_length=1, max_length=8)

    @model_validator(mode="after")
    def unique_claims(self) -> "ConclusionView":
        claim_ids = [claim.id for claim in self.claims]
        if len(claim_ids) != len(set(claim_ids)):
            raise ValueError("duplicate id")
        return self


class Interaction(BaseModel):
    model_config = ConfigDict(extra="forbid")

    speaker: Literal["player", "narration"]
    text: str

    @model_validator(mode="after")
    def limit_text(self) -> "Interaction":
        cap = PLAYER_TEXT_MAX if self.speaker == "player" else NARRATION_TEXT_MAX
        self.text = bounded_text(self.text, 1, cap, "interaction length")
        return self


class AuthorizedContext(BaseModel):
    model_config = ConfigDict(extra="forbid")

    locationName: str
    presentNpcs: list[NpcView] = Field(default_factory=list, max_length=12)
    availableActions: list[ActionView] = Field(default_factory=list, max_length=32)
    unavailableActions: list[IdStr] = Field(default_factory=list, max_length=32)
    requirementBlockedActions: list[IdStr] = Field(default_factory=list, max_length=32)
    allowedParameterKeys: list[IdStr] = Field(default_factory=list, max_length=8)
    parameterValueIds: list[IdStr] = Field(default_factory=list, max_length=32)
    recentInteractions: list[Interaction] = Field(default_factory=list, max_length=8)
    narrativeSummary: str = ""
    availableConclusions: list[ConclusionView] = Field(default_factory=list, max_length=4)
    revealedLines: list[str] = Field(default_factory=list, max_length=24)

    @field_validator("locationName")
    @classmethod
    def location_name(cls, value: str) -> str:
        return bounded_text(value, 1, DISPLAY_MAX, "location name length")

    @field_validator("narrativeSummary")
    @classmethod
    def summary(cls, value: str) -> str:
        return bounded_text(value, 0, SUMMARY_MAX, "summary length")

    @field_validator("revealedLines")
    @classmethod
    def revealed_lines(cls, value: list[str]) -> list[str]:
        return [bounded_text(item, 1, NARRATION_TEXT_MAX, "revealed line length") for item in value]

    @field_validator(
        "unavailableActions",
        "requirementBlockedActions",
        "allowedParameterKeys",
        "parameterValueIds",
    )
    @classmethod
    def unique_ids(cls, value: list[str]) -> list[str]:
        if len(value) != len(set(value)):
            raise ValueError("duplicate id")
        return value

    @model_validator(mode="after")
    def unique_views(self) -> "AuthorizedContext":
        npc_ids = [npc.id for npc in self.presentNpcs]
        action_ids = [action.id for action in self.availableActions]
        conclusion_ids = [item.id for item in self.availableConclusions]
        if (
            len(npc_ids) != len(set(npc_ids))
            or len(action_ids) != len(set(action_ids))
            or len(conclusion_ids) != len(set(conclusion_ids))
        ):
            raise ValueError("duplicate id")
        return self


class ConfirmedCheck(BaseModel):
    """A roll Kotlin already made. The fields have to agree with each other."""

    model_config = ConfigDict(extra="forbid")

    attribute: str
    roll: int = Field(ge=1, le=20)
    modifier: int = Field(ge=1, le=5)
    total: int
    difficulty: int
    success: bool

    @model_validator(mode="after")
    def consistent(self) -> "ConfirmedCheck":
        if self.attribute not in {"STRENGTH", "AGILITY", "INTELLIGENCE"}:
            raise ValueError("attribute")
        if self.difficulty not in {8, 12, 16, 20}:
            raise ValueError("difficulty")
        if self.total != self.roll + self.modifier:
            raise ValueError("total")
        if self.success != (self.total >= self.difficulty):
            raise ValueError("success")
        return self


def assert_scene_alignment(contract: SceneContract, context: AuthorizedContext) -> None:
    """The visible cast and the offered actions stay inside the scene contract."""

    available = {action.id for action in context.availableActions}
    unavailable = set(context.unavailableActions)
    blocked = set(context.requirementBlockedActions)
    if not available <= set(contract.allowedActionIds):
        raise ValueError("available action is outside the scene contract")
    if available & unavailable or available & blocked or unavailable & blocked:
        raise ValueError("action id is in more than one list")
    npc_ids = {npc.id for npc in context.presentNpcs}
    if npc_ids != set(contract.presentNpcIds):
        raise ValueError("present npcs do not match the scene contract")


def contains_forbidden(haystacks: list[str], forbidden: list[str]) -> bool:
    if not forbidden:
        return False
    combined = "\n".join(haystacks).casefold()
    return any(secret.casefold() in combined for secret in forbidden)


def bounded_text(value: str, minimum: int, maximum: int, message: str) -> str:
    text = value.strip()
    if len(text) < minimum or len(text) > maximum:
        raise ValueError(message)
    return text
