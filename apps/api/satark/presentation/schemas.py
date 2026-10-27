"""Pydantic DTOs — the API contract shared with the web client."""

from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, Field


class AnalyzeRequest(BaseModel):
    text: str = Field(..., min_length=1, max_length=4000, description="The SMS / WhatsApp message")
    upi_id: str | None = Field(None, max_length=120)
    url: str | None = Field(None, max_length=500)


class EntityDTO(BaseModel):
    kind: Literal["upi_vpa", "phone", "url", "amount", "credential", "bank", "app"]
    value: str
    valid: bool | None = None
    note: str | None = None


class SignalDTO(BaseModel):
    name: str
    label: str
    score: float
    weight: float
    evidence: str
    latency_ms: int
    details: dict[str, Any] = {}


class StageDTO(BaseModel):
    index: int
    name: str
    label: str
    status: Literal["pending", "running", "done", "skipped", "failed"]
    latency_ms: int = 0
    signal: SignalDTO | None = None
    error: str | None = None


class VerdictDTO(BaseModel):
    label: Literal["SAFE", "SUSPICIOUS", "FRAUD"]
    score: int
    confidence: float
    reasons: list[str]
    advice: list[str]


class AnalysisResultDTO(BaseModel):
    text: str
    upi_id: str | None
    url: str | None
    language: str
    language_confidence: float
    translated_text: str | None
    entities: list[EntityDTO]
    signals: list[SignalDTO]
    stages: list[StageDTO]
    verdict: VerdictDTO
    total_latency_ms: int


class StageEventDTO(BaseModel):
    """One SSE event. `type` is `plan` (initial stage list), `stage`, or `result`."""

    type: Literal["plan", "stage", "result", "error"]
    stage: StageDTO | None = None
    stages: list[StageDTO] | None = None
    result: AnalysisResultDTO | None = None
    message: str | None = None


class ModelStatusDTO(BaseModel):
    name: str
    model_id: str
    loaded: bool


class HealthDTO(BaseModel):
    status: Literal["ok", "loading"]
    models: list[ModelStatusDTO]
