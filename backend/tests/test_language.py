from dungeon_master_ai.context.builder import SPANISH_NARRATE, SPANISH_SCENE, SPANISH_VOICE
from payloads import interpretation, narration, scene


def test_spanish_requests_instruct_narration_and_scenes(client, provider):
    provider.script.append('{"text":"Lees la carta.","auxiliary":{}}')
    narrated = client.post("/v1/narrate", json=narration(narrativeLanguage="es"))
    assert narrated.status_code == 200
    assert "Write the narration in natural Spanish." in provider.calls[0]["system"]
    assert SPANISH_NARRATE in provider.calls[0]["system"]
    assert "Do not translate technical ids." not in provider.calls[0]["system"]
    assert "Do not copy identifiers" in provider.calls[0]["system"]
    assert SPANISH_VOICE in provider.calls[0]["system"]
    assert "second person" in provider.calls[0]["system"]
    assert "present tense" in provider.calls[0]["system"]
    assert "mystery and dread" in provider.calls[0]["system"]

    provider.script.append('{"text":"La plaza espera.","auxiliary":{}}')
    generated = client.post("/v1/scenes", json=scene(narrativeLanguage="es"))
    assert generated.status_code == 200
    assert "Write the scene in natural Spanish." in provider.calls[1]["system"]
    assert SPANISH_SCENE in provider.calls[1]["system"]
    assert "Do not translate technical ids." not in provider.calls[1]["system"]
    assert SPANISH_VOICE in provider.calls[1]["system"]


def test_spanish_interpretation_keeps_ids(client, provider):
    provider.script.append(
        '{"status":"RECOGNIZED","candidateActionId":"ask_mara",'
        '"candidateActionIds":[],"parameters":{}}'
    )
    response = client.post("/v1/interpret", json=interpretation(narrativeLanguage="es"))
    assert response.status_code == 200
    system = provider.calls[0]["system"]
    assert "The player sentence is Spanish." in system
    assert "Do not translate those ids." in system
    assert "second person" not in system
    assert "mystery and dread" not in system


def test_omitted_language_does_not_add_the_spanish_instruction(client, provider):
    provider.script.append('{"text":"Mara turns away.","auxiliary":{}}')
    response = client.post("/v1/narrate", json=narration())
    assert response.status_code == 200
    assert "natural Spanish" not in provider.calls[0]["system"]


def test_an_unknown_narrative_language_is_rejected(client):
    response = client.post("/v1/narrate", json=narration(narrativeLanguage="en"))
    assert response.status_code == 422
