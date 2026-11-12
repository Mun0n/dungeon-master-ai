import os
from collections.abc import Mapping, MutableMapping
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urlparse

_PROVIDERS = {"ollama", "openai_compatible"}
_LOG_LEVELS = {"DEBUG", "INFO", "WARNING", "ERROR"}


class SettingsError(Exception):
    """The process cannot start with the current environment."""


@dataclass(frozen=True)
class Settings:
    llm_provider: str
    ollama_base_url: str
    ollama_model: str | None
    llm_base_url: str | None
    llm_model: str | None
    llm_api_key: str | None
    llm_timeout_seconds: float
    host: str
    port: int
    log_level: str

    @property
    def model_name(self) -> str:
        if self.llm_provider == "ollama":
            if self.ollama_model is None:
                raise SettingsError("OLLAMA_MODEL is required.")
            return self.ollama_model
        if self.llm_model is None:
            raise SettingsError("LLM_MODEL is required.")
        return self.llm_model


def load_local_env(path: Path, environ: MutableMapping[str, str] | None = None) -> None:
    """Fill missing variables from a local file. Existing variables stay as they are."""

    target = os.environ if environ is None else environ
    if not path.is_file():
        return
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line[len("export ") :].strip()
        key, separator, value = line.partition("=")
        if separator != "=":
            continue
        key = key.strip()
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in {'"', "'"}:
            value = value[1:-1]
        if key and key not in target:
            target[key] = value


def load_settings(environ: Mapping[str, str] | None = None) -> Settings:
    env = os.environ if environ is None else environ
    provider = _required(env, "LLM_PROVIDER")
    if provider not in _PROVIDERS:
        raise SettingsError("LLM_PROVIDER must be ollama or openai_compatible.")

    ollama_base_url = env.get("OLLAMA_BASE_URL", "http://127.0.0.1:11434").strip()
    ollama_model = _blank_as_none(env.get("OLLAMA_MODEL", ""))
    llm_base_url = _blank_as_none(env.get("LLM_BASE_URL", ""))
    llm_model = _blank_as_none(env.get("LLM_MODEL", ""))
    llm_api_key = _blank_as_none(env.get("LLM_API_KEY", ""))

    if provider == "ollama":
        _require_http_url(ollama_base_url, "OLLAMA_BASE_URL")
        if ollama_model is None:
            raise SettingsError("OLLAMA_MODEL is required.")
    else:
        if llm_base_url is None:
            raise SettingsError("LLM_BASE_URL is required.")
        _require_http_url(llm_base_url, "LLM_BASE_URL")
        if llm_model is None:
            raise SettingsError("LLM_MODEL is required.")
        if llm_api_key is None:
            raise SettingsError("LLM_API_KEY is required.")

    timeout = _positive_float(env.get("LLM_TIMEOUT_SECONDS", "30"), "LLM_TIMEOUT_SECONDS")
    if timeout > 120:
        raise SettingsError("LLM_TIMEOUT_SECONDS must be at most 120.")
    host = env.get("HOST", "127.0.0.1").strip() or "127.0.0.1"
    if any(character.isspace() for character in host):
        raise SettingsError("HOST must not contain whitespace.")
    port = _port(env.get("PORT", "8000"))
    log_level = env.get("LOG_LEVEL", "INFO").strip().upper() or "INFO"
    if log_level not in _LOG_LEVELS:
        raise SettingsError("LOG_LEVEL must be DEBUG, INFO, WARNING, or ERROR.")

    return Settings(
        llm_provider=provider,
        ollama_base_url=ollama_base_url.rstrip("/"),
        ollama_model=ollama_model,
        llm_base_url=None if llm_base_url is None else llm_base_url.rstrip("/"),
        llm_model=llm_model,
        llm_api_key=llm_api_key,
        llm_timeout_seconds=timeout,
        host=host,
        port=port,
        log_level=log_level,
    )


def _required(env: Mapping[str, str], name: str) -> str:
    value = env.get(name, "").strip()
    if not value:
        raise SettingsError(f"{name} is required.")
    return value


def _blank_as_none(value: str) -> str | None:
    stripped = value.strip()
    if not stripped:
        return None
    return stripped


def _require_http_url(value: str, name: str) -> None:
    parsed = urlparse(value)
    if parsed.scheme not in {"http", "https"} or parsed.hostname is None:
        raise SettingsError(f"{name} must be an http or https URL.")
    if parsed.username is not None or parsed.password is not None or parsed.query or parsed.fragment:
        raise SettingsError(f"{name} must not carry credentials.")


def _positive_float(value: str, name: str) -> float:
    try:
        parsed = float(value.strip())
    except ValueError as error:
        raise SettingsError(f"{name} must be a number.") from error
    if parsed <= 0:
        raise SettingsError(f"{name} must be greater than zero.")
    return parsed


def _port(value: str) -> int:
    try:
        parsed = int(value.strip())
    except ValueError as error:
        raise SettingsError("PORT must be an integer.") from error
    if parsed < 1 or parsed > 65535:
        raise SettingsError("PORT must be from 1 through 65535.")
    return parsed
