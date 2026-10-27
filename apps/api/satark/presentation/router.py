from __future__ import annotations

import asyncio
import json
import logging
from collections.abc import AsyncIterator

from fastapi import APIRouter, Depends, Request
from sse_starlette.sse import EventSourceResponse
from starlette.concurrency import run_in_threadpool

from satark.application import AnalyzeMessageUseCase
from satark.domain import Message

from .mappers import result_to_dto, stage_to_dto
from .schemas import AnalysisResultDTO, AnalyzeRequest, HealthDTO, ModelStatusDTO, StageEventDTO

log = logging.getLogger("satark.api")
router = APIRouter(prefix="/api")

_SENTINEL = object()


def get_use_case(request: Request) -> AnalyzeMessageUseCase:
    return request.app.state.use_case


def get_models(request: Request) -> dict[str, object]:
    return request.app.state.models


@router.get("/health", response_model=HealthDTO)
def health(models: dict[str, object] = Depends(get_models)) -> HealthDTO:
    statuses = [
        ModelStatusDTO(name=name, model_id=getattr(m, "active_model_id", getattr(m, "model_id", "?")), loaded=bool(getattr(m, "loaded", False)))
        for name, m in models.items()
    ]
    return HealthDTO(status="ok" if all(s.loaded for s in statuses) else "loading", models=statuses)


@router.post("/analyze", response_model=AnalysisResultDTO)
async def analyze(req: AnalyzeRequest, use_case: AnalyzeMessageUseCase = Depends(get_use_case)) -> AnalysisResultDTO:
    message = Message(text=req.text, upi_id=req.upi_id or None, url=req.url or None)
    result = await run_in_threadpool(use_case.execute, message)
    return result_to_dto(result)


@router.post("/analyze/stream")
async def analyze_stream(req: AnalyzeRequest, request: Request, use_case: AnalyzeMessageUseCase = Depends(get_use_case)):
    message = Message(text=req.text, upi_id=req.upi_id or None, url=req.url or None)
    loop = asyncio.get_running_loop()
    queue: asyncio.Queue = asyncio.Queue()

    def producer() -> None:
        try:
            for event in use_case.stream(message):
                loop.call_soon_threadsafe(queue.put_nowait, event)
        except Exception as exc:  # noqa: BLE001
            log.exception("pipeline failed")
            loop.call_soon_threadsafe(queue.put_nowait, exc)
        finally:
            loop.call_soon_threadsafe(queue.put_nowait, _SENTINEL)

    async def events() -> AsyncIterator[dict]:
        plan = StageEventDTO(type="plan", stages=[stage_to_dto(s) for s in use_case.stage_plan()])
        yield {"event": "plan", "data": plan.model_dump_json()}

        task = loop.run_in_executor(None, producer)
        while True:
            if await request.is_disconnected():
                break
            item = await queue.get()
            if item is _SENTINEL:
                break
            if isinstance(item, Exception):
                yield {"event": "error", "data": StageEventDTO(type="error", message=str(item)).model_dump_json()}
                break
            if item.result is not None:
                dto = StageEventDTO(type="result", stage=stage_to_dto(item.stage), result=result_to_dto(item.result))
                yield {"event": "result", "data": dto.model_dump_json()}
            else:
                dto = StageEventDTO(type="stage", stage=stage_to_dto(item.stage))
                yield {"event": "stage", "data": dto.model_dump_json()}
        await task

    return EventSourceResponse(events(), ping=15)


__all__ = ["router", "json"]
