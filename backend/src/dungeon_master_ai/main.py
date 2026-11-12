import logging
import time
from pathlib import Path

import uvicorn
from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from dungeon_master_ai.errors import INVALID_REQUEST, ApiError
from dungeon_master_ai.providers.base import LlmProvider
from dungeon_master_ai.providers.ollama import OllamaProvider
from dungeon_master_ai.providers.openai_compatible import OpenAiCompatibleProvider
from dungeon_master_ai.routes.health import router as health_router
from dungeon_master_ai.schemas.common import CONTRACT_VERSION
from dungeon_master_ai.routes.interpret import router as interpret_router
from dungeon_master_ai.routes.narrate import router as narrate_router
from dungeon_master_ai.routes.scenes import router as scenes_router
from dungeon_master_ai.settings import Settings, load_local_env, load_settings

logger = logging.getLogger("dungeon_master_ai")


def create_app(settings: Settings, provider: LlmProvider) -> FastAPI:
    quiet_provider_logs()
    app = FastAPI(
        title="Dungeon Master AI",
        version=str(CONTRACT_VERSION),
        telemetry={
            "tracing": False,
            "metrics": False,
            "logs": False,
            "auto_configure": False,
        },
    )
    app.state.settings = settings
    app.state.provider = provider
    app.include_router(health_router)
    app.include_router(interpret_router)
    app.include_router(narrate_router)
    app.include_router(scenes_router)
    app.add_exception_handler(ApiError, _api_error)
    app.add_exception_handler(RequestValidationError, _invalid_request)

    @app.middleware("http")
    async def log_request(request: Request, call_next):
        started = time.perf_counter()
        response = await call_next(request)
        elapsed_ms = int((time.perf_counter() - started) * 1000)
        logger.info(
            "request method=%s path=%s status=%s error=%s latency_ms=%s",
            request.method,
            request.url.path,
            response.status_code,
            getattr(request.state, "error_code", "-"),
            elapsed_ms,
        )
        return response

    return app


def build_provider(settings: Settings) -> LlmProvider:
    if settings.llm_provider == "ollama":
        return OllamaProvider(settings)
    return OpenAiCompatibleProvider(settings)


def quiet_provider_logs() -> None:
    """Provider clients can log request headers. Keep those loggers quiet."""

    logging.getLogger("httpx").setLevel(logging.WARNING)
    logging.getLogger("httpcore").setLevel(logging.WARNING)


def configure_logging(level: str) -> None:
    logging.basicConfig(level=level, format="%(levelname)s %(name)s %(message)s")
    quiet_provider_logs()


async def _api_error(request: Request, exc: ApiError) -> JSONResponse:
    request.state.error_code = exc.code
    return JSONResponse(status_code=exc.status_code, content=exc.body())


async def _invalid_request(request: Request, exc: RequestValidationError) -> JSONResponse:
    del exc
    request.state.error_code = INVALID_REQUEST
    return JSONResponse(
        status_code=422,
        content={"error": {"code": INVALID_REQUEST, "retryable": False, "attempt": 1}},
    )


def main() -> None:
    load_local_env(Path.cwd() / ".env")
    settings = load_settings()
    configure_logging(settings.log_level)
    uvicorn.run(
        create_app(settings, build_provider(settings)),
        host=settings.host,
        port=settings.port,
        log_level=settings.log_level.lower(),
        access_log=False,
    )
