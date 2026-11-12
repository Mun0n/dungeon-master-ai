from concurrent.futures import ThreadPoolExecutor

from payloads import AMBIGUOUS, FORBIDDEN, RECOGNIZED, interpretation
from dungeon_master_ai.routes.interpret import interpret_sentence
from dungeon_master_ai.schemas.interpret import InterpretationRequest

from conftest import ScriptedProvider, assert_error


def test_valid_interpretation_echoes_the_request(client, provider):
    provider.script.append(RECOGNIZED)
    response = client.post("/v1/interpret", json=interpretation())
    body = response.json()
    assert response.status_code == 200
    assert body["status"] == "RECOGNIZED"
    assert body["candidateActionId"] == "ask_mara"
    assert body["candidateActionIds"] == []
    assert body["parameters"] == {}
    assert body["requestId"] == "req-1"
    assert body["stateRevision"] == "rev-1"
    assert body["sceneContractId"] == "plaza_arrival"
    assert body["contractVersion"] == 1
    assert "text" not in body
    assert "effects" not in body


def test_ambiguous_interpretation_has_no_single_action(client, provider):
    provider.script.append(AMBIGUOUS)
    response = client.post("/v1/interpret", json=interpretation())
    body = response.json()
    assert response.status_code == 200
    assert body["status"] == "AMBIGUOUS"
    assert body["candidateActionId"] is None
    assert body["candidateActionIds"] == ["ask_mara", "read_notice"]


def test_unavailable_and_blocked_statuses(client, provider):
    provider.script.append(
        '{"status":"UNAVAILABLE","candidateActionId":"open_crypt",'
        '"candidateActionIds":[],"parameters":{}}'
    )
    provider.script.append(
        '{"status":"REQUIREMENTS_REJECTED","candidateActionId":"accuse_local",'
        '"candidateActionIds":[],"parameters":{}}'
    )
    unavailable = client.post("/v1/interpret", json=interpretation())
    blocked = client.post("/v1/interpret", json=interpretation())
    assert unavailable.json()["status"] == "UNAVAILABLE"
    assert blocked.json()["status"] == "REQUIREMENTS_REJECTED"


def test_fenced_json_is_accepted(client, provider):
    provider.script.append("```json\n" + RECOGNIZED + "\n```")
    response = client.post("/v1/interpret", json=interpretation())
    assert response.status_code == 200
    assert response.json()["candidateActionId"] == "ask_mara"


def test_parameters_must_be_in_the_allow_list(client, provider):
    body = interpretation()
    body["authorizedContext"]["allowedParameterKeys"] = ["target"]
    body["authorizedContext"]["parameterValueIds"] = ["mara"]
    provider.script.append(
        '{"status":"RECOGNIZED","candidateActionId":"ask_mara",'
        '"candidateActionIds":[],"parameters":{"target":"mara"}}'
    )
    accepted = client.post("/v1/interpret", json=body)
    assert accepted.status_code == 200
    assert accepted.json()["parameters"] == {"target": "mara"}

    provider.script.extend(
        [
            '{"status":"RECOGNIZED","candidateActionId":"ask_mara",'
            '"candidateActionIds":[],"parameters":{"target":"aldren"}}',
            '{"status":"RECOGNIZED","candidateActionId":"ask_mara",'
            '"candidateActionIds":[],"parameters":{"target":"aldren"}}',
        ]
    )
    rejected = client.post("/v1/interpret", json=body)
    assert_error(rejected, 422, "CONTRACT_VIOLATION", False, 2)


def test_unauthorized_action_is_rejected(client, provider):
    bad = (
        '{"status":"RECOGNIZED","candidateActionId":"open_crypt",'
        '"candidateActionIds":[],"parameters":{}}'
    )
    provider.script.extend([bad, bad])
    response = client.post("/v1/interpret", json=interpretation())
    assert_error(response, 422, "CONTRACT_VIOLATION", False, 2)
    assert "open_crypt" not in response.text


def test_model_cannot_replace_the_request_id(client, provider):
    stale = (
        '{"status":"RECOGNIZED","candidateActionId":"ask_mara",'
        '"candidateActionIds":[],"parameters":{},"requestId":"other-request"}'
    )
    provider.script.extend([stale, stale])
    response = client.post("/v1/interpret", json=interpretation())
    assert_error(response, 422, "INVALID_MODEL_OUTPUT", True, 2)
    assert "other-request" not in response.text


def test_each_request_keeps_its_own_scene(client, provider):
    provider.script.extend([RECOGNIZED, RECOGNIZED])
    first = interpretation()
    second = interpretation()
    second["requestId"] = "req-two"
    second["stateRevision"] = "rev-two"
    second["sceneContract"]["id"] = "scene_two"
    one = client.post("/v1/interpret", json=first)
    two = client.post("/v1/interpret", json=second)
    assert one.json()["sceneContractId"] == "plaza_arrival"
    assert two.json()["requestId"] == "req-two"
    assert two.json()["stateRevision"] == "rev-two"
    assert two.json()["sceneContractId"] == "scene_two"


def test_concurrent_interpretations_keep_their_ids():
    provider = ScriptedProvider([RECOGNIZED, RECOGNIZED])
    one = interpretation()
    one["requestId"] = "req-one"
    one["sceneContract"]["id"] = "scene_one"
    two = interpretation()
    two["requestId"] = "req-two"
    two["sceneContract"]["id"] = "scene_two"
    with ThreadPoolExecutor(max_workers=2) as pool:
        futures = [
            pool.submit(
                interpret_sentence,
                InterpretationRequest.model_validate(one),
                provider,
            ),
            pool.submit(
                interpret_sentence,
                InterpretationRequest.model_validate(two),
                provider,
            ),
        ]
        results = [future.result() for future in futures]
    assert {item.requestId: item.sceneContractId for item in results} == {
        "req-one": "scene_one",
        "req-two": "scene_two",
    }


def test_prompt_receives_the_authorized_view_only(client, provider):
    provider.script.append(RECOGNIZED)
    client.post("/v1/interpret", json=interpretation())
    call = provider.calls[0]
    assert FORBIDDEN not in call["system"]
    assert FORBIDDEN not in call["user"]
    assert "The bells ring at midnight." in call["user"]
    assert "ask_mara" in call["user"]
    assert "CHANGE_LIFE" not in call["user"]
    assert "appliedEffects" not in call["user"]
    assert "forbiddenInformation" not in call["user"]
    assert isinstance(call["json_schema"], dict)


def test_player_text_limit(client, provider):
    provider.script.append(RECOGNIZED)
    accepted = interpretation(playerText="p" * 500)
    assert client.post("/v1/interpret", json=accepted).status_code == 200
    rejected = interpretation(playerText="p" * 501)
    response = client.post("/v1/interpret", json=rejected)
    assert_error(response, 422, "INVALID_REQUEST", False, 1)
    assert "p" * 501 not in response.text


def test_summary_limit(client, provider):
    provider.script.append(RECOGNIZED)
    accepted = interpretation()
    accepted["authorizedContext"]["narrativeSummary"] = "s" * 3000
    assert client.post("/v1/interpret", json=accepted).status_code == 200
    rejected = interpretation()
    rejected["authorizedContext"]["narrativeSummary"] = "s" * 3001
    response = client.post("/v1/interpret", json=rejected)
    assert_error(response, 422, "INVALID_REQUEST", False, 1)


def test_context_that_contains_a_secret_does_not_call_the_model(client, provider):
    provider.script.append(RECOGNIZED)
    body = interpretation()
    body["authorizedContext"]["narrativeSummary"] = "Then Aldren keeps the crypt key nearby. The yard is quiet."
    response = client.post("/v1/interpret", json=body)
    assert response.status_code == 200
    assert provider.calls
    assert FORBIDDEN not in provider.calls[0]["user"]
    assert "The yard is quiet." in provider.calls[0]["user"]
    calls = len(provider.calls)
    revealable = interpretation()
    revealable["sceneContract"]["revealableInformation"] = [FORBIDDEN]
    response = client.post("/v1/interpret", json=revealable)
    assert_error(response, 422, "CONTEXT_REJECTED", False, 1)
    assert len(provider.calls) == calls
    quoted = interpretation()
    quoted["sceneContract"]["forbiddenInformation"] = ['Aldren said "the crypt key"']
    quoted["playerText"] = 'I repeat that Aldren said "the crypt key" aloud.'
    response = client.post("/v1/interpret", json=quoted)
    assert_error(response, 422, "CONTEXT_REJECTED", False, 1)
    assert len(provider.calls) == calls


def test_short_forbidden_string_is_rejected(client):
    body = interpretation()
    body["sceneContract"]["forbiddenInformation"] = ["secret1"]
    response = client.post("/v1/interpret", json=body)
    assert_error(response, 422, "INVALID_REQUEST", False, 1)


def test_extra_request_field_is_rejected(client):
    body = interpretation()
    body["effects"] = [{"type": "CHANGE_LIFE", "amount": -1}]
    response = client.post("/v1/interpret", json=body)
    assert_error(response, 422, "INVALID_REQUEST", False, 1)
    assert "CHANGE_LIFE" not in response.text


def test_mismatched_npc_list_is_rejected(client):
    body = interpretation()
    body["authorizedContext"]["presentNpcs"] = []
    response = client.post("/v1/interpret", json=body)
    assert_error(response, 422, "INVALID_REQUEST", False, 1)


def test_retry_does_not_echo_the_rejected_reply(client, provider):
    provider.script.extend(["LEAKED_MODEL_TEXT", RECOGNIZED])
    response = client.post("/v1/interpret", json=interpretation())
    assert response.status_code == 200
    assert len(provider.calls) == 2
    assert "LEAKED_MODEL_TEXT" not in provider.calls[1]["user"]
    assert "response is not a json object" in provider.calls[1]["user"]


def test_health_does_not_call_the_provider(client, provider):
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok", "provider": "ollama", "model": "test-model"}
    assert provider.calls == []


def test_logs_omit_player_text_and_secrets(client, provider, caplog):
    import logging

    caplog.set_level(logging.INFO, logger="dungeon_master_ai")
    provider.script.append(RECOGNIZED)
    response = client.post(
        "/v1/interpret",
        json=interpretation(playerText="UNIQUE_PLAYER_SENTENCE"),
    )
    assert response.status_code == 200
    assert "UNIQUE_PLAYER_SENTENCE" not in caplog.text
    assert FORBIDDEN not in caplog.text
    assert "error=-" in caplog.text


def _offered_conclusion():
    return {
        "id": "name_the_crime",
        "claims": [
            {
                "id": "recent_killings",
                "prompt": "¿A quién culpa la frase de los asesinatos recientes?",
                "options": [
                    {"id": "alden", "gloss": "Culpa a Padre Aldren."},
                    {"id": "unknown", "gloss": "No atribuye los asesinatos a nadie."},
                ],
            },
            {
                "id": "confinement",
                "prompt": "¿Qué dice la frase sobre las personas sanas?",
                "options": [
                    {"id": "shut_in_with_the_sick", "gloss": "Las encerró con los enfermos."},
                    {"id": "unknown", "gloss": "No lo dice."},
                ],
            },
        ],
    }


def test_conclusion_accepts_one_option_for_each_offered_claim(client, provider):
    body = interpretation()
    body["authorizedContext"]["availableConclusions"] = [_offered_conclusion()]
    provider.script.append(
        '{"status":"CONCLUSION","candidateActionId":null,"candidateActionIds":[],'
        '"parameters":{},"claims":['
        '{"claimId":"recent_killings","optionId":"alden"},'
        '{"claimId":"confinement","optionId":"unknown"}]}'
    )
    response = client.post("/v1/interpret", json=body)
    assert response.status_code == 200
    payload = response.json()
    assert payload["status"] == "CONCLUSION"
    assert payload["candidateActionId"] is None
    assert payload["claims"] == [
        {"claimId": "recent_killings", "optionId": "alden"},
        {"claimId": "confinement", "optionId": "unknown"},
    ]
    assert "accept" not in response.text
    user = provider.calls[0]["user"]
    assert "availableConclusions" in user
    assert "shut_in_with_the_sick" in user
    assert "did not commit the murders" in provider.calls[0]["system"]


def test_conclusion_with_an_action_or_unknown_claim_is_rejected(client, provider):
    body = interpretation()
    body["authorizedContext"]["availableConclusions"] = [_offered_conclusion()]
    mixed = (
        '{"status":"CONCLUSION","candidateActionId":"ask_mara","candidateActionIds":[],'
        '"parameters":{},"claims":['
        '{"claimId":"recent_killings","optionId":"alden"},'
        '{"claimId":"confinement","optionId":"unknown"}]}'
    )
    unknown = (
        '{"status":"CONCLUSION","candidateActionId":null,"candidateActionIds":[],'
        '"parameters":{},"claims":['
        '{"claimId":"recent_killings","optionId":"beltran"},'
        '{"claimId":"confinement","optionId":"unknown"}]}'
    )
    provider.script.extend([mixed, mixed, unknown, unknown])
    mixed_response = client.post("/v1/interpret", json=body)
    unknown_response = client.post("/v1/interpret", json=body)
    assert_error(mixed_response, 422, "CONTRACT_VIOLATION", False, 2)
    assert_error(unknown_response, 422, "CONTRACT_VIOLATION", False, 2)
    correction = provider.calls[1]["user"]
    assert "exactly one claims entry for each of these claim ids: recent_killings, confinement" in correction
    assert mixed not in correction


def test_conclusion_is_rejected_when_none_is_offered(client, provider):
    bare = (
        '{"status":"CONCLUSION","candidateActionId":null,"candidateActionIds":[],'
        '"parameters":{},"claims":[]}'
    )
    provider.script.extend([bare, bare])
    response = client.post("/v1/interpret", json=interpretation())
    assert_error(response, 422, "CONTRACT_VIOLATION", False, 2)


def test_an_action_reply_cannot_carry_claims(client, provider):
    claimed = (
        '{"status":"RECOGNIZED","candidateActionId":"ask_mara",'
        '"candidateActionIds":[],"parameters":{},'
        '"claims":[{"claimId":"recent_killings","optionId":"alden"}]}'
    )
    provider.script.extend([claimed, claimed])
    response = client.post("/v1/interpret", json=interpretation())
    assert_error(response, 422, "CONTRACT_VIOLATION", False, 2)
