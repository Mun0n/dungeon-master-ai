from payloads import scene, prose
from conftest import assert_error


def test_generation_is_skipped_when_the_scene_does_not_require_it(client, provider):
    response = client.post("/v1/scenes", json=scene(required=False))
    assert_error(response, 422, "GENERATION_NOT_REQUIRED", False, 1)
    assert provider.calls == []


def test_generated_scene_limit_is_2000_characters(client, provider):
    provider.script.append(prose("s" * 2000))
    accepted = client.post("/v1/scenes", json=scene())
    assert accepted.status_code == 200
    assert len(accepted.json()["text"]) == 2000
    assert accepted.json()["sceneContractId"] == "plaza_arrival"
    assert "effects" not in accepted.json()

    provider.script.extend([prose("s" * 2001), prose("s" * 2001)])
    rejected = client.post("/v1/scenes", json=scene())
    assert_error(rejected, 422, "INVALID_MODEL_OUTPUT", True, 2)


def test_scene_prompt_omits_forbidden_information(client, provider):
    from payloads import FORBIDDEN

    provider.script.append(prose("The plaza is quiet."))
    client.post("/v1/scenes", json=scene())
    call = provider.calls[0]
    assert "The bells ring at midnight." in call["user"]
    assert FORBIDDEN not in call["user"]
    assert FORBIDDEN not in call["system"]
    assert "appliedEffects" not in call["user"]
