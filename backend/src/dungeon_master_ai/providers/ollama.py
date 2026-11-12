import httpx

from dungeon_master_ai.providers.transport import message_text, post_for_json
from dungeon_master_ai.settings import Settings


class OllamaProvider:
    def __init__(self, settings: Settings, client: httpx.Client | None = None) -> None:
        self._model = settings.ollama_model
        self._url = f"{settings.ollama_base_url}/api/chat"
        self._client = client or httpx.Client(
            timeout=settings.llm_timeout_seconds,
            follow_redirects=False,
        )

    def complete(self, *, system: str, user: str, json_schema: dict) -> str:
        payload = {
            "model": self._model,
            "stream": False,
            "format": json_schema,
            "messages": [
                {"role": "system", "content": system},
                {"role": "user", "content": user},
            ],
            "options": {"temperature": 0},
        }
        body = post_for_json(self._client, self._url, payload)
        return message_text(body, "message", "content")
