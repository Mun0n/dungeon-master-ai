import copy


FORBIDDEN = "Aldren keeps the crypt key"

RECOGNIZED = (
    '{"status":"RECOGNIZED","candidateActionId":"ask_mara",'
    '"candidateActionIds":[],"parameters":{}}'
)

AMBIGUOUS = (
    '{"status":"AMBIGUOUS","candidateActionId":null,'
    '"candidateActionIds":["ask_mara","read_notice"],"parameters":{}}'
)

PROSE = '{"text":"Mara turns away.","auxiliary":{}}'


def scene_contract():
    return {
        "id": "plaza_arrival",
        "locationId": "plaza",
        "purpose": "Arrive and ask one question.",
        "allowedActionIds": ["ask_mara", "read_notice"],
        "presentNpcIds": ["mara"],
        "revealableInformation": ["The bells ring at midnight."],
        "forbiddenInformation": [FORBIDDEN],
    }


def context():
    return {
        "locationName": "Plaza",
        "presentNpcs": [{"id": "mara", "name": "Mara", "state": "wary"}],
        "availableActions": [
            {"id": "ask_mara", "label": "Ask Mara"},
            {"id": "read_notice", "label": "Read the notice"},
        ],
        "unavailableActions": ["open_crypt"],
        "requirementBlockedActions": ["accuse_local"],
        "allowedParameterKeys": [],
        "parameterValueIds": [],
        "recentInteractions": [],
        "narrativeSummary": "",
    }


def interpretation(**changes):
    body = {
        "contractVersion": 1,
        "requestId": "req-1",
        "stateRevision": "rev-1",
        "sceneContract": scene_contract(),
        "authorizedContext": context(),
        "playerText": "I ask Mara about the bells",
    }
    body.update(changes)
    return copy.deepcopy(body)


def narration(outcome="FAILED", **changes):
    if outcome == "REJECTED":
        resolved = {
            "actionId": None,
            "outcome": "REJECTED",
            "check": None,
            "appliedEffects": [],
        }
    elif outcome == "SUCCEEDED":
        resolved = {
            "actionId": "ask_mara",
            "outcome": "SUCCEEDED",
            "check": None,
            "appliedEffects": [{"type": "ADD_CLUE", "clueId": "bell_schedule"}],
        }
    else:
        resolved = {
            "actionId": "ask_mara",
            "outcome": "FAILED",
            "check": {
                "attribute": "INTELLIGENCE",
                "roll": 4,
                "modifier": 5,
                "total": 9,
                "difficulty": 12,
                "success": False,
            },
            "appliedEffects": [{"type": "CHANGE_LIFE", "amount": -1}],
        }
    body = {
        "contractVersion": 1,
        "requestId": "req-1",
        "stateRevision": "rev-1",
        "sceneContract": scene_contract(),
        "authorizedContext": context(),
        "resolvedAction": resolved,
        "lifeAfter": 9,
        "allowedAuxiliaryKeys": [],
    }
    body.update(changes)
    return copy.deepcopy(body)


def scene(required=True, **changes):
    body = {
        "contractVersion": 1,
        "requestId": "req-1",
        "stateRevision": "rev-1",
        "generationRequired": required,
        "sceneContract": scene_contract(),
        "authorizedContext": context(),
        "allowedAuxiliaryKeys": [],
    }
    body.update(changes)
    return copy.deepcopy(body)


def prose(text, auxiliary=None):
    import json

    return json.dumps({"text": text, "auxiliary": auxiliary or {}})
