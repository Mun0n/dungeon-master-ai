from typing import Annotated, Literal, Union

from pydantic import BaseModel, ConfigDict, Field, field_validator

from dungeon_master_ai.schemas.common import IdStr


class SetFlagEffect(BaseModel):
    model_config = ConfigDict(extra="forbid")

    type: Literal["SET_FLAG"]
    key: IdStr
    value: bool


class ChangeLifeEffect(BaseModel):
    model_config = ConfigDict(extra="forbid")

    type: Literal["CHANGE_LIFE"]
    amount: int

    @field_validator("amount")
    @classmethod
    def nonzero(cls, value: int) -> int:
        if value == 0:
            raise ValueError("amount")
        return value


class AddItemEffect(BaseModel):
    model_config = ConfigDict(extra="forbid")

    type: Literal["ADD_ITEM"]
    itemId: IdStr


class RemoveItemEffect(BaseModel):
    model_config = ConfigDict(extra="forbid")

    type: Literal["REMOVE_ITEM"]
    itemId: IdStr


class DiscoverLocationEffect(BaseModel):
    model_config = ConfigDict(extra="forbid")

    type: Literal["DISCOVER_LOCATION"]
    locationId: IdStr


class MoveToEffect(BaseModel):
    model_config = ConfigDict(extra="forbid")

    type: Literal["MOVE_TO"]
    locationId: IdStr


class SetRelationshipEffect(BaseModel):
    model_config = ConfigDict(extra="forbid")

    type: Literal["SET_RELATIONSHIP"]
    npcId: IdStr
    value: int


class AddEvidenceEffect(BaseModel):
    model_config = ConfigDict(extra="forbid")

    type: Literal["ADD_EVIDENCE"]
    evidenceId: IdStr


class AddClueEffect(BaseModel):
    model_config = ConfigDict(extra="forbid")

    type: Literal["ADD_CLUE"]
    clueId: IdStr


class SetNpcStateEffect(BaseModel):
    model_config = ConfigDict(extra="forbid")

    type: Literal["SET_NPC_STATE"]
    npcId: IdStr
    state: IdStr


class RecordAttemptEffect(BaseModel):
    model_config = ConfigDict(extra="forbid")

    type: Literal["RECORD_ATTEMPT"]
    actionId: IdStr


class TriggerEventEffect(BaseModel):
    model_config = ConfigDict(extra="forbid")

    type: Literal["TRIGGER_EVENT"]
    eventId: IdStr


class SetEndingEffect(BaseModel):
    model_config = ConfigDict(extra="forbid")

    type: Literal["SET_ENDING"]
    endingId: IdStr


AppliedEffect = Annotated[
    Union[
        SetFlagEffect,
        ChangeLifeEffect,
        AddItemEffect,
        RemoveItemEffect,
        DiscoverLocationEffect,
        MoveToEffect,
        SetRelationshipEffect,
        AddEvidenceEffect,
        AddClueEffect,
        SetNpcStateEffect,
        RecordAttemptEffect,
        TriggerEventEffect,
        SetEndingEffect,
    ],
    Field(discriminator="type"),
]
