import json

from dungeon_master_ai.context.builder import NARRATION_USE
from dungeon_master_ai.validation.redaction import redact_summary
from payloads import FORBIDDEN, PROSE, narration, prose
from conftest import assert_error


def test_failed_outcome_is_copied_from_the_request(client, provider):
    provider.script.append(PROSE)
    response = client.post("/v1/narrate", json=narration())
    body = response.json()
    assert response.status_code == 200
    assert body["outcome"] == "FAILED"
    assert body["text"] == "Mara turns away."
    assert body["auxiliary"] == {}
    assert "effects" not in body
    call = provider.calls[0]
    assert '"outcome":"not completed"' in call["user"]
    assert '"location":"Plaza"' in call["user"]
    assert '"atributo":"Inteligencia"' in call["user"]
    assert '"dado":4' in call["user"]
    assert "No supera la prueba" in call["user"]
    assert '"life":9' in call["user"]
    assert "CHANGE_LIFE" not in call["user"]
    assert "locationId" not in call["user"]
    assert "ask_mara" not in call["user"]
    assert "INTELLIGENCE" not in call["user"]
    assert "wary" not in call["user"]
    assert "ADD_ITEM" not in call["user"]
    assert "availableActions" not in call["user"]
    assert FORBIDDEN not in call["user"]
    assert FORBIDDEN not in call["system"]


def test_model_cannot_change_the_outcome_or_attach_effects(client, provider):
    bad = (
        '{"text":"Mara welcomes you inside.","auxiliary":{},'
        '"outcome":"SUCCEEDED","effects":[{"type":"ADD_ITEM","itemId":"crypt_key"}]}'
    )
    provider.script.extend([bad, PROSE])
    response = client.post("/v1/narrate", json=narration())
    assert response.status_code == 200
    assert response.json()["outcome"] == "FAILED"
    assert "effects" not in response.json()
    assert "crypt_key" not in response.text


def test_effect_field_never_becomes_a_valid_reply(client, provider):
    bad = (
        '{"text":"Mara turns away.","auxiliary":{},'
        '"effects":[{"type":"CHANGE_LIFE","amount":-1}]}'
    )
    provider.script.extend([bad, bad])
    response = client.post("/v1/narrate", json=narration())
    assert_error(response, 422, "INVALID_MODEL_OUTPUT", True, 2)
    assert "CHANGE_LIFE" not in response.text


def test_forbidden_information_in_the_reply_is_rejected(client, provider):
    bad = json.dumps({"text": "Aldren keeps the crypt key under the cloth.", "auxiliary": {}})
    provider.script.extend([bad, bad])
    response = client.post("/v1/narrate", json=narration())
    assert_error(response, 422, "CONTRACT_VIOLATION", False, 2)
    assert FORBIDDEN not in response.text
    assert FORBIDDEN not in provider.calls[1]["user"]


def test_forbidden_reply_can_be_replaced_by_a_valid_retry(client, provider):
    bad = json.dumps({"text": "Aldren keeps the crypt key under the cloth.", "auxiliary": {}})
    provider.script.extend([bad, PROSE])
    response = client.post("/v1/narrate", json=narration())
    assert response.status_code == 200
    assert response.json()["text"] == "Mara turns away."
    assert FORBIDDEN not in provider.calls[1]["user"]
    assert "response contains forbidden information" in provider.calls[1]["user"]


def test_narration_limit_is_1200_characters(client, provider):
    provider.script.append(prose("n" * 1200))
    accepted = client.post("/v1/narrate", json=narration())
    assert accepted.status_code == 200
    assert len(accepted.json()["text"]) == 1200
    provider.script.extend([prose("n" * 1201), prose("n" * 1201)])
    rejected = client.post("/v1/narrate", json=narration())
    assert_error(rejected, 422, "INVALID_MODEL_OUTPUT", True, 2)


def test_narrative_summary_auxiliary_limit(client, provider):
    body = narration()
    body["allowedAuxiliaryKeys"] = ["narrativeSummary"]
    provider.script.append(prose("Mara turns away.", {"narrativeSummary": "s" * 3000}))
    accepted = client.post("/v1/narrate", json=body)
    assert accepted.status_code == 200
    assert len(accepted.json()["auxiliary"]["narrativeSummary"]) == 3000

    provider.script.extend(
        [
            prose("Mara turns away.", {"narrativeSummary": "s" * 3001}),
            prose("Mara turns away.", {"narrativeSummary": "s" * 3001}),
        ]
    )
    rejected = client.post("/v1/narrate", json=body)
    assert_error(rejected, 422, "INVALID_MODEL_OUTPUT", True, 2)


def test_auxiliary_key_must_be_allowed(client, provider):
    bad = prose("Mara turns away.", {"notes": "A side comment."})
    provider.script.extend([bad, bad])
    response = client.post("/v1/narrate", json=narration())
    assert_error(response, 422, "CONTRACT_VIOLATION", False, 2)


def test_unknown_effect_and_inconsistent_check_are_rejected(client):
    unknown = narration()
    unknown["resolvedAction"]["appliedEffects"] = [{"type": "GRANT_WISH", "amount": 1}]
    response = client.post("/v1/narrate", json=unknown)
    assert_error(response, 422, "INVALID_REQUEST", False, 1)
    assert "GRANT_WISH" not in response.text

    inconsistent = narration()
    inconsistent["resolvedAction"]["check"]["total"] = 99
    response = client.post("/v1/narrate", json=inconsistent)
    assert_error(response, 422, "INVALID_REQUEST", False, 1)


def test_rejection_carries_no_effects(client, provider):
    provider.script.append(prose("Nothing happens."))
    response = client.post("/v1/narrate", json=narration("REJECTED"))
    assert response.status_code == 200
    assert response.json()["outcome"] == "REJECTED"
    smuggled = narration("REJECTED")
    smuggled["resolvedAction"]["appliedEffects"] = [{"type": "CHANGE_LIFE", "amount": -1}]
    response = client.post("/v1/narrate", json=smuggled)
    assert_error(response, 422, "INVALID_REQUEST", False, 1)


def test_empty_narrative_summary_is_omitted(client, provider):
    body = narration()
    body["allowedAuxiliaryKeys"] = ["narrativeSummary"]
    provider.script.append(prose("Mara turns away.", {"narrativeSummary": "  "}))
    response = client.post("/v1/narrate", json=body)
    assert response.status_code == 200
    assert response.json()["text"] == "Mara turns away."
    assert response.json()["auxiliary"] == {}


def test_confirmed_life_and_ending_are_read_only_context(client, provider):
    provider.script.append(PROSE)
    body = narration()
    body["lifeAfter"] = 0
    body["endingId"] = "death"
    response = client.post("/v1/narrate", json=body)
    assert response.status_code == 200
    call = provider.calls[0]
    assert '"life":0' in call["user"]
    assert "Final: la caída acaba contigo" in call["user"]
    assert "endingId" not in call["user"]
    assert '"death"' not in call["user"]
    assert "endingId" not in response.json()
    omitted = narration()
    provider.script.append(PROSE)
    quiet = client.post("/v1/narrate", json=omitted)
    assert quiet.status_code == 200
    assert '"life":9' in provider.calls[1]["user"]
    assert "ending" not in provider.calls[1]["user"]


def test_resolved_action_outside_the_contract_is_rejected(client):
    body = narration()
    body["resolvedAction"]["actionId"] = "open_crypt"
    response = client.post("/v1/narrate", json=body)
    assert_error(response, 422, "INVALID_REQUEST", False, 1)


def test_confirmed_action_can_be_narrated_after_it_is_no_longer_offered(client, provider):
    provider.script.append(PROSE)
    body = narration()
    body["authorizedContext"]["availableActions"] = []
    body["authorizedContext"]["unavailableActions"] = ["ask_mara", "read_notice"]
    response = client.post("/v1/narrate", json=body)
    assert response.status_code == 200
    assert response.json()["outcome"] == "FAILED"
    assert response.json()["text"] == "Mara turns away."


def test_punctuation_after_an_anaphora_still_drops_the_follower():
    text = "Elia está muerta. Ella, no regresará. Tampoco. Ellas siguen en la plaza."
    assert redact_summary(text, ["Elia está muerta"]) == "Ellas siguen en la plaza."


def test_a_mixed_summary_keeps_the_calm_sentence(client, provider):
    body = narration()
    body["allowedAuxiliaryKeys"] = ["narrativeSummary"]
    body["sceneContract"]["forbiddenInformation"] = ["Elia está muerta"]
    provider.script.append(
        prose(
            "Mara turns away.",
            {"narrativeSummary": "Elia está muerta. Ella no regresará. La plaza sigue en calma."},
        )
    )
    response = client.post("/v1/narrate", json=body)
    assert response.status_code == 200
    assert response.json()["text"] == "Mara turns away."
    assert response.json()["auxiliary"]["narrativeSummary"] == "La plaza sigue en calma."

    provider.script.extend(
        [
            prose("Elia está muerta en la plaza.", {}),
            prose("Elia está muerta en la plaza.", {}),
        ]
    )
    rejected = client.post("/v1/narrate", json=body)
    assert_error(rejected, 422, "CONTRACT_VIOLATION", False, 2)


def test_narration_prompt_hides_technical_tokens(client, provider):
    provider.script.append(PROSE)
    body = narration()
    body["authorizedContext"]["revealedLines"] = ["La carta nombra a Elia."]
    body["resolvedAction"]["actionId"] = "read_elias_letter"
    body["sceneContract"]["allowedActionIds"] = ["read_elias_letter", "ask_mara", "read_notice"]
    response = client.post("/v1/narrate", json=body)
    assert response.status_code == 200
    user = provider.calls[0]["user"]
    assert "La carta nombra a Elia." in user
    assert '"location":"Plaza"' in user
    for token in ("read_elias_letter", "SET_FLAG", "justice", "crypt", "unwarned", "CHANGE_LIFE"):
        assert token not in user


def test_narration_puts_the_confirmed_place_ahead_of_an_old_summary(client, provider):
    provider.script.append(PROSE)
    body = narration("SUCCEEDED")
    body["narrativeLanguage"] = "es"
    body["hora"] = "22:10"
    body["hecho"] = "Entras en el archivo."
    body["authorizedContext"]["locationName"] = "Archivo"
    body["authorizedContext"]["narrativeSummary"] = (
        "Te encuentras en la plaza. La bruma se cierne y el silencio pesa."
    )
    body["authorizedContext"]["recentInteractions"] = [
        {"speaker": "narration", "text": "La bruma se cierne sobre la plaza."}
    ]
    response = client.post("/v1/narrate", json=body)
    assert response.status_code == 200
    call = provider.calls[0]
    user = call["user"]
    summary = user.index('"narrativeSummary"')
    assert user.index('"location":"Archivo"') < summary
    assert user.index('"hora":"22:10"') < summary
    assert user.index('"hecho":"Entras en el archivo."') < summary
    assert "where the player is now" in call["system"]
    assert "later hour" in call["system"]
    assert "Do not repeat a sensory image" in call["system"]
    assert "not a list of forbidden words" in call["system"]
    assert "Finding a path or an entrance is not the same as going there" in call["system"]
    assert "Do not recite the clock" in call["system"]
    assert "do not write that it is 22:10" in call["system"]
    assert "or as about to happen" in call["system"]


def test_a_failed_check_stays_in_front_of_the_summary(client, provider):
    provider.script.append(PROSE)
    body = narration()
    body["hora"] = "22:10"
    body["hecho"] = "La plaza está oscura y no distingues nada útil."
    body["authorizedContext"]["narrativeSummary"] = "La bruma se cierne."
    response = client.post("/v1/narrate", json=body)
    assert response.status_code == 200
    user = provider.calls[0]["user"]
    assert user.index('"resultado":"No supera la prueba"') < user.index('"narrativeSummary"')
    assert '"hora":"22:10"' in user


def test_a_successful_check_is_named_before_the_summary(client, provider):
    provider.script.append(PROSE)
    body = narration("SUCCEEDED")
    body["hora"] = "23:00"
    body["lifeAfter"] = 10
    body["hecho"] = "Entre las sombras distingues el campanario."
    body["authorizedContext"]["narrativeSummary"] = "El silencio pesa."
    body["resolvedAction"]["check"] = {
        "attribute": "AGILITY",
        "roll": 12,
        "modifier": 2,
        "total": 14,
        "difficulty": 8,
        "success": True,
    }
    body["resolvedAction"]["appliedEffects"] = []
    response = client.post("/v1/narrate", json=body)
    assert response.status_code == 200
    user = provider.calls[0]["user"]
    assert '"resultado":"Supera la prueba"' in user
    assert user.index('"resultado":"Supera la prueba"') < user.index('"narrativeSummary"')
    assert '"hora":"23:00"' in user
    assert '"life":10' in user
    assert user.index('"hora":"23:00"') < user.index(NARRATION_USE)
    assert "Do not write a clock time." in user
    system = provider.calls[0]["system"]
    assert "Do not recite the clock, the life total, the die, the attribute, or the difficulty." in system
    assert "what the character does or experiences" in system
    for field in ('"dado":12', '"atributo":"Agilidad"', '"dificultad":8'):
        assert field in user


def test_an_impossible_hour_is_rejected(client):
    body = narration()
    body["hora"] = "24:10"
    response = client.post("/v1/narrate", json=body)
    assert_error(response, 422, "INVALID_REQUEST", False, 1)


def test_success_without_a_check_describes_only_the_applied_effect(client, provider):
    provider.script.append(PROSE)
    response = client.post("/v1/narrate", json=narration("SUCCEEDED"))
    assert response.status_code == 200
    assert response.json()["outcome"] == "SUCCEEDED"
    assert "ADD_CLUE" not in provider.calls[0]["user"]
    assert '"outcome":"completed"' in provider.calls[0]["user"]
    assert "CHANGE_LIFE" not in provider.calls[0]["user"]
