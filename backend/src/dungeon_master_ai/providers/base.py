from typing import Protocol


class ProviderTimeout(Exception):
    """The provider did not answer before the configured timeout."""


class ProviderFailure(Exception):
    """The provider returned an unusable transport result."""


class InvalidModelOutput(Exception):
    """The provider rejected the completion as invalid JSON.

    The message is a fixed reason. The provider body is not stored on this exception.
    """


class LlmProvider(Protocol):
    def complete(self, *, system: str, user: str, json_schema: dict) -> str:
        """Return the model text. Raise ProviderTimeout or ProviderFailure on transport errors."""
