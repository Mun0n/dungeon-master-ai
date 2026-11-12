from collections.abc import Callable

from dungeon_master_ai.errors import (
    INVALID_MODEL_OUTPUT,
    PROVIDER_ERROR,
    PROVIDER_TIMEOUT,
    ApiError,
    ModelOutputError,
)
from dungeon_master_ai.providers.base import (
    InvalidModelOutput,
    LlmProvider,
    ProviderFailure,
    ProviderTimeout,
)


def call_with_one_retry(provider: LlmProvider, system: str, user: str, schema: dict, accept: Callable):
    """Call the provider. A schema or contract failure gets one corrected attempt.

    A timeout or a provider error stops immediately. The correction note lists
    safe reasons and does not include the rejected reply.
    """

    failure: ModelOutputError | None = None
    for attempt in (1, 2):
        prompt = user if failure is None else _correction(user, failure.safe_messages)
        try:
            raw = provider.complete(system=system, user=prompt, json_schema=schema)
        except ProviderTimeout:
            raise ApiError(PROVIDER_TIMEOUT, 504, True, attempt) from None
        except InvalidModelOutput:
            failure = ModelOutputError(INVALID_MODEL_OUTPUT, ["response is not a json object"])
            continue
        except ProviderFailure:
            raise ApiError(PROVIDER_ERROR, 502, True, attempt) from None
        try:
            return accept(raw)
        except ModelOutputError as error:
            failure = error
    assert failure is not None
    raise ApiError(failure.public_code, 422, failure.public_code == INVALID_MODEL_OUTPUT, 2)


def _correction(user: str, reasons: list[str]) -> str:
    detail = "; ".join(reasons) if reasons else "response failed schema validation"
    return (
        f"{user}\n\nThe previous response was rejected. "
        f"Reasons: {detail}. Return one JSON object and no other text."
    )
