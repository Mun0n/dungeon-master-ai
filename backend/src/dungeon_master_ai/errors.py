INVALID_REQUEST = "INVALID_REQUEST"
CONTEXT_REJECTED = "CONTEXT_REJECTED"
GENERATION_NOT_REQUIRED = "GENERATION_NOT_REQUIRED"
INVALID_MODEL_OUTPUT = "INVALID_MODEL_OUTPUT"
CONTRACT_VIOLATION = "CONTRACT_VIOLATION"
PROVIDER_TIMEOUT = "PROVIDER_TIMEOUT"
PROVIDER_ERROR = "PROVIDER_ERROR"


class ApiError(Exception):
    """A failure the client can read. The message is the public code only."""

    def __init__(self, code: str, status_code: int, retryable: bool, attempt: int) -> None:
        super().__init__(code)
        self.code = code
        self.status_code = status_code
        self.retryable = retryable
        self.attempt = attempt

    def body(self) -> dict[str, object]:
        return {
            "error": {
                "code": self.code,
                "retryable": self.retryable,
                "attempt": self.attempt,
            }
        }


class ModelOutputError(Exception):
    """The model reply failed schema or contract checks.

    safe_messages are short reasons that may be shown back to the model.
    They must not quote player text, secrets, or the rejected reply.
    """

    def __init__(self, public_code: str, safe_messages: list[str]) -> None:
        super().__init__(public_code)
        self.public_code = public_code
        self.safe_messages = safe_messages
