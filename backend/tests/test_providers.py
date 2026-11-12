import json

import httpx
import pytest

from dungeon_master_ai.main import quiet_provider_logs
from dungeon_master_ai.providers.base import InvalidModelOutput, ProviderFailure, ProviderTimeout
from dungeon_master_ai.providers.ollama import OllamaProvider
from dungeon_master_ai.providers.openai_compatible import OpenAiCompatibleProvider
from dungeon_master_ai.settings import SettingsError, load_local_env, load_settings

KEY = "super-secret-key-xyz"


def _ollama_settings():
    return load_settings(
        {
            "LLM_PROVIDER": "ollama",
            "OLLAMA_MODEL": "test-model",
            "OLLAMA_BASE_URL": "http://127.0.0.1:11434",
        }
    )


def _external_settings():
    return load_settings(
        {
            "LLM_PROVIDER": "openai_compatible",
            "LLM_BASE_URL": "https://example.test/v1",
            "LLM_MODEL": "external-model",
            "LLM_API_KEY": KEY,
        }
    )


def test_ollama_sends_the_schema_and_does_not_stream():
    seen = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["url"] = str(request.url)
        seen["body"] = json.loads(request.content)
        return httpx.Response(200, json={"message": {"content": "{\"ok\":true}"}})

    provider = OllamaProvider(_ollama_settings(), httpx.Client(transport=httpx.MockTransport(handler)))
    assert provider.complete(system="sys", user="user", json_schema={"type": "object"}) == '{"ok":true}'
    assert seen["url"] == "http://127.0.0.1:11434/api/chat"
    assert seen["body"]["model"] == "test-model"
    assert seen["body"]["stream"] is False
    assert seen["body"]["options"]["temperature"] == 0
    assert seen["body"]["format"] == {"type": "object"}
    assert seen["body"]["messages"][0]["role"] == "system"


def test_ollama_timeout_and_http_error_hide_the_transport_body():
    def timeout(request: httpx.Request) -> httpx.Response:
        raise httpx.ReadTimeout("timed out", request=request)

    provider = OllamaProvider(_ollama_settings(), httpx.Client(transport=httpx.MockTransport(timeout)))
    with pytest.raises(ProviderTimeout) as timeout_error:
        provider.complete(system="sys", user="user", json_schema={})
    assert str(timeout_error.value) == "provider timed out"
    assert timeout_error.value.__cause__ is None

    def failed(request: httpx.Request) -> httpx.Response:
        return httpx.Response(429, json={"error": KEY})

    provider = OllamaProvider(_ollama_settings(), httpx.Client(transport=httpx.MockTransport(failed)))
    with pytest.raises(ProviderFailure) as failure:
        provider.complete(system="sys", user="user", json_schema={})
    assert str(failure.value) == "provider request failed"
    assert KEY not in str(failure.value)
    assert failure.value.__cause__ is None


def test_external_provider_sends_the_key_and_the_schema_without_leaking_either():
    seen = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["authorization"] = request.headers["authorization"]
        seen["body"] = json.loads(request.content)
        return httpx.Response(
            200,
            json={"choices": [{"message": {"content": "{\"text\":\"Hello.\"}"}}]},
        )

    provider = OpenAiCompatibleProvider(
        _external_settings(),
        httpx.Client(transport=httpx.MockTransport(handler)),
    )
    text = provider.complete(system="sys", user="user", json_schema={"type": "object", "title": "Reply"})
    assert text == '{"text":"Hello."}'
    assert seen["authorization"] == f"Bearer {KEY}"
    assert seen["body"]["response_format"] == {"type": "json_object"}
    assert seen["body"]["temperature"] == 0
    assert "JSON schema:" in seen["body"]["messages"][0]["content"]
    assert '"title":"Reply"' in seen["body"]["messages"][0]["content"]

    def failed(request: httpx.Request) -> httpx.Response:
        assert request.headers["authorization"] == f"Bearer {KEY}"
        return httpx.Response(500, text=KEY)

    provider = OpenAiCompatibleProvider(
        _external_settings(),
        httpx.Client(transport=httpx.MockTransport(failed)),
    )
    with pytest.raises(ProviderFailure) as failure:
        provider.complete(system="sys", user="user", json_schema={})
    assert KEY not in str(failure.value)
    assert failure.value.__cause__ is None


def test_groq_json_validation_is_invalid_output_and_hides_the_body():
    leaked = f"Failed to validate JSON. {KEY}"

    def rejected(request: httpx.Request) -> httpx.Response:
        return httpx.Response(400, text=leaked)

    provider = OpenAiCompatibleProvider(
        _external_settings(),
        httpx.Client(transport=httpx.MockTransport(rejected)),
    )
    with pytest.raises(InvalidModelOutput) as failure:
        provider.complete(system="sys", user="user", json_schema={})
    assert str(failure.value) == "response is not a json object"
    assert KEY not in str(failure.value)
    assert "Failed to validate JSON" not in str(failure.value)
    assert failure.value.__cause__ is None

    def other(request: httpx.Request) -> httpx.Response:
        return httpx.Response(400, text=KEY)

    provider = OpenAiCompatibleProvider(
        _external_settings(),
        httpx.Client(transport=httpx.MockTransport(other)),
    )
    with pytest.raises(ProviderFailure) as provider_failure:
        provider.complete(system="sys", user="user", json_schema={})
    assert KEY not in str(provider_failure.value)


def test_settings_require_the_selected_provider():
    with pytest.raises(SettingsError):
        load_settings({"LLM_PROVIDER": "ollama"})
    with pytest.raises(SettingsError):
        load_settings(
            {
                "LLM_PROVIDER": "openai_compatible",
                "LLM_BASE_URL": "https://example.test/v1",
                "LLM_MODEL": "external-model",
                "LLM_API_KEY": "",
            }
        )
    with pytest.raises(SettingsError):
        load_settings(
            {
                "LLM_PROVIDER": "ollama",
                "OLLAMA_MODEL": "test-model",
                "OLLAMA_BASE_URL": "http://user:secret@127.0.0.1:11434",
            }
        )
    with pytest.raises(SettingsError):
        load_settings(
            {
                "LLM_PROVIDER": "openai_compatible",
                "LLM_BASE_URL": "https://example.test/v1?api_key=super-secret-key-xyz",
                "LLM_MODEL": "external-model",
                "LLM_API_KEY": "super-secret-key-xyz",
            }
        )


def test_local_env_fills_blanks_without_override(tmp_path):
    path = tmp_path / ".env"
    path.write_text('export OLLAMA_MODEL="from-file"\nLLM_PROVIDER=openai_compatible\n# comment\n\n')
    target = {"LLM_PROVIDER": "ollama"}
    load_local_env(path, target)
    assert target["LLM_PROVIDER"] == "ollama"
    assert target["OLLAMA_MODEL"] == "from-file"


def test_provider_loggers_stay_at_warning():
    import logging

    logging.getLogger("httpx").setLevel(logging.DEBUG)
    quiet_provider_logs()
    assert logging.getLogger("httpx").level == logging.WARNING
    assert logging.getLogger("httpcore").level == logging.WARNING
