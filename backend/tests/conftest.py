import threading

import pytest
from fastapi.testclient import TestClient

from dungeon_master_ai.main import create_app
from dungeon_master_ai.settings import load_settings


class ScriptedProvider:
    def __init__(self, script=None):
        self.script = list(script or [])
        self.calls = []
        self._lock = threading.Lock()

    def complete(self, *, system, user, json_schema):
        with self._lock:
            self.calls.append({"system": system, "user": user, "json_schema": json_schema})
            if not self.script:
                raise AssertionError("unexpected provider call")
            item = self.script.pop(0)
        if isinstance(item, BaseException):
            raise item
        if callable(item):
            return item(system=system, user=user, json_schema=json_schema)
        return item


@pytest.fixture
def provider():
    return ScriptedProvider()


@pytest.fixture
def client(provider):
    settings = load_settings({"LLM_PROVIDER": "ollama", "OLLAMA_MODEL": "test-model"})
    app = create_app(settings, provider)
    with TestClient(app) as test_client:
        yield test_client


def assert_error(response, status, code, retryable, attempt):
    assert response.status_code == status
    assert response.json() == {
        "error": {"code": code, "retryable": retryable, "attempt": attempt}
    }
