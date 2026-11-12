import httpx

from dungeon_master_ai.providers.base import InvalidModelOutput, ProviderFailure, ProviderTimeout


def post_for_json(
    client: httpx.Client,
    url: str,
    payload: dict,
    headers: dict[str, str] | None = None,
) -> dict:
    """POST JSON and return an object. Transport details stay out of the exception text."""

    try:
        response = client.post(url, json=payload, headers=headers)
    except httpx.TimeoutException:
        raise ProviderTimeout("provider timed out") from None
    except httpx.HTTPError:
        raise ProviderFailure("provider request failed") from None
    if response.status_code != 200:
        if _json_validation_rejected(response):
            raise InvalidModelOutput("response is not a json object")
        raise ProviderFailure("provider request failed")
    try:
        body = response.json()
    except ValueError:
        raise ProviderFailure("provider request failed") from None
    if not isinstance(body, dict):
        raise ProviderFailure("provider request failed")
    return body


def _json_validation_rejected(response: httpx.Response) -> bool:
    """Read the body only to choose the path. The text is not returned."""

    if response.status_code != 400:
        return False
    try:
        text = response.text
    except Exception:
        return False
    return "Failed to validate JSON" in text


def message_text(body: dict, *path: str) -> str:
    value: object = body
    for key in path:
        if not isinstance(value, dict) or key not in value:
            raise ProviderFailure("provider request failed")
        value = value[key]
    if not isinstance(value, str):
        raise ProviderFailure("provider request failed")
    return value
