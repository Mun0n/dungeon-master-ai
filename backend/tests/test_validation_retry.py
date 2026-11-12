from payloads import RECOGNIZED, interpretation
from dungeon_master_ai.providers.base import InvalidModelOutput, ProviderFailure, ProviderTimeout

from conftest import assert_error


def test_malformed_reply_retries_once_then_fails(client, provider):
    provider.script.extend(["LEAKED_MODEL_TEXT", "LEAKED_MODEL_TEXT"])
    response = client.post("/v1/interpret", json=interpretation())
    assert_error(response, 422, "INVALID_MODEL_OUTPUT", True, 2)
    assert len(provider.calls) == 2
    assert "LEAKED_MODEL_TEXT" not in response.text


def test_invalid_json_validation_retries_once_without_the_provider_body(client, provider):
    leaked = "Failed to validate JSON. secret-body-should-stay-hidden"
    provider.script.extend(
        [
            InvalidModelOutput("response is not a json object"),
            InvalidModelOutput("response is not a json object"),
        ]
    )
    response = client.post("/v1/interpret", json=interpretation())
    assert_error(response, 422, "INVALID_MODEL_OUTPUT", True, 2)
    assert len(provider.calls) == 2
    assert leaked not in response.text
    assert leaked not in provider.calls[1]["user"]
    assert "response is not a json object" in provider.calls[1]["user"]
    assert "Failed to validate JSON" not in provider.calls[1]["user"]


def test_timeout_does_not_retry(client, provider):
    provider.script.append(ProviderTimeout("provider timed out"))
    response = client.post("/v1/interpret", json=interpretation())
    assert_error(response, 504, "PROVIDER_TIMEOUT", True, 1)
    assert len(provider.calls) == 1


def test_provider_failure_is_an_error_envelope(client, provider):
    provider.script.append(ProviderFailure("provider request failed"))
    response = client.post("/v1/interpret", json=interpretation())
    assert_error(response, 502, "PROVIDER_ERROR", True, 1)
    assert len(provider.calls) == 1
    assert "provider request failed" not in response.text


def test_a_timeout_on_the_validation_retry_stops(client, provider):
    provider.script.extend(["not-json", ProviderTimeout("provider timed out")])
    response = client.post("/v1/interpret", json=interpretation())
    assert_error(response, 504, "PROVIDER_TIMEOUT", True, 2)
    assert len(provider.calls) == 2


def test_valid_reply_is_not_sent_twice(client, provider):
    provider.script.append(RECOGNIZED)
    response = client.post("/v1/interpret", json=interpretation())
    assert response.status_code == 200
    assert len(provider.calls) == 1
