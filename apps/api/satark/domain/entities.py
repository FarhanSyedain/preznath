"""Domain entities and value objects.

This module has zero framework or ML imports on purpose. Everything here is
plain Python so the core model of the problem can be reasoned about and tested
without loading a single model.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import Any


class VerdictLabel(str, Enum):
    SAFE = "SAFE"
    SUSPICIOUS = "SUSPICIOUS"
    FRAUD = "FRAUD"


class EntityKind(str, Enum):
    UPI_VPA = "upi_vpa"
    PHONE = "phone"
    URL = "url"
    AMOUNT = "amount"
    CREDENTIAL = "credential"  # OTP / PIN / CVV mentions
    BANK = "bank"
    APP = "app"  # remote-access apps, APK


class StageStatus(str, Enum):
    PENDING = "pending"
    RUNNING = "running"
    DONE = "done"
    SKIPPED = "skipped"
    FAILED = "failed"


@dataclass(frozen=True)
class Message:
    """What the user pasted. Immutable input to the pipeline."""

    text: str
    upi_id: str | None = None
    url: str | None = None

    def combined_text(self) -> str:
        parts = [self.text.strip()]
        if self.upi_id:
            parts.append(self.upi_id.strip())
        if self.url:
            parts.append(self.url.strip())
        return "\n".join(p for p in parts if p)


@dataclass(frozen=True)
class Entity:
    kind: EntityKind
    value: str
    valid: bool | None = None  # e.g. VPA handle recognised
    note: str | None = None  # e.g. "lookalike of okaxis"


@dataclass(frozen=True)
class Signal:
    """One pipeline stage's contribution to the risk score."""

    name: str  # machine name, e.g. "spam"
    label: str  # human label, e.g. "Spam classifier"
    score: float  # 0..1, 1 = most risky
    weight: float  # relative weight in fusion
    evidence: str  # one or two plain sentences
    latency_ms: int = 0
    details: dict[str, Any] = field(default_factory=dict)

    def clamped(self) -> float:
        return max(0.0, min(1.0, self.score))


@dataclass(frozen=True)
class PipelineStage:
    index: int
    name: str
    label: str
    status: StageStatus
    latency_ms: int = 0
    signal: Signal | None = None
    error: str | None = None


@dataclass(frozen=True)
class Verdict:
    label: VerdictLabel
    score: int  # 0..100
    confidence: float  # 0..1 — how far from the nearest threshold
    reasons: list[str]
    advice: list[str]


@dataclass(frozen=True)
class AnalysisResult:
    message: Message
    language: str
    language_confidence: float
    translated_text: str | None
    entities: list[Entity]
    signals: list[Signal]
    stages: list[PipelineStage]
    verdict: Verdict
    total_latency_ms: int
