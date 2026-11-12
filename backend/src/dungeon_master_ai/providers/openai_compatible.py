import json

import httpx

from dungeon_master_ai.providers.base import ProviderFailure
from dungeon_master_ai.providers.transport import post_for_json
from dungeon_master_ai.settings import Settings


class OpenAiCompatibleProvider:
    def __init__(self, settings: Settings, client: httpx.Client | None = None) -> None:
        self._model = settings.llm_model
        self._url = f"{settings.llm_base_url}/chat/completions"
        self._api_key = settings.llm_api_key or ""
        self._client = client or httpx.Client(
            timeout=settings.llm_timeout_seconds,
            follow_redirects=False,
        )

    def complete(self, *, system: str, user: str, json_schema: dict) -> str:
        instructed = (
            system
            + "\nJSON schema:\n"
            + json.dumps(json_schema, separators=(",", ":"), ensure_ascii=False)
        )
        payload = {
            "model": self._model,
            "temperature": 0,
            "response_format": {"type": "json_object"},
            "messages": [
                {"role": "system", "content": instructed},
                {"role": "user", "content": user},
            ],
        }
        body = post_for_json(
            self._client,
            self._url,
            payload,
            headers={"Authorization": f"Bearer {self._api_key}"},
        )
        choices = body.get("choices")
        if not isinstance(choices, list) or not choices or not isinstance(choices[0], dict):
            raise ProviderFailure("provider request failed")
        message = choices[0].get("message")
        if not isinstance(message, dict) or not isinstance(message.get("content"), str):
            raise ProviderFailure("provider request failed")
        return message["content"]
