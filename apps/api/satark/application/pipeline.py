"""The Stage interface and the mutable context that flows between stages."""

from __future__ import annotations

import time
from abc import ABC, abstractmethod
from dataclasses import dataclass, field

from satark.domain import Entity, Message, Signal


@dataclass
class PipelineContext:
    message: Message
    original_text: str
    text: str  # working text (English if we translated)
    language: str = "en"
    language_confidence: float = 0.0
    translated_text: str | None = None
    entities: list[Entity] = field(default_factory=list)
    signals: list[Signal] = field(default_factory=list)

    def signal(self, name: str) -> Signal | None:
        return next((s for s in self.signals if s.name == name), None)


class Stage(ABC):
    """One step of the pipeline. Stages are composed in order by the use case."""

    name: str
    label: str
    weight: float

    @abstractmethod
    def run(self, ctx: PipelineContext) -> Signal: ...

    def timed(self, ctx: PipelineContext) -> Signal:
        start = time.perf_counter()
        signal = self.run(ctx)
        elapsed = int((time.perf_counter() - start) * 1000)
        return Signal(
            name=signal.name,
            label=signal.label,
            score=signal.clamped(),
            weight=signal.weight,
            evidence=signal.evidence,
            latency_ms=elapsed,
            details=signal.details,
        )
