"""AnalyzeMessageUseCase — orchestrates the stages and emits progress events."""

from __future__ import annotations

import time
from collections.abc import Iterator
from dataclasses import dataclass

from satark.domain import AnalysisResult, Message, PipelineStage, Signal, StageStatus, Verdict

from .pipeline import PipelineContext, Stage
from .risk_fusion import RiskFusionService

FUSION_INDEX = 8


@dataclass(frozen=True)
class StageEvent:
    """Emitted once per stage transition; the UI lights rows up from these."""

    stage: PipelineStage
    result: AnalysisResult | None = None  # set only on the final event


class AnalyzeMessageUseCase:
    def __init__(self, stages: list[Stage], fusion: RiskFusionService):
        self._stages = stages
        self._fusion = fusion

    def stage_plan(self) -> list[PipelineStage]:
        plan = [PipelineStage(i + 1, s.name, s.label, StageStatus.PENDING) for i, s in enumerate(self._stages)]
        plan.append(PipelineStage(FUSION_INDEX, "fusion", "Risk fusion", StageStatus.PENDING))
        return plan

    def execute(self, message: Message) -> AnalysisResult:
        result: AnalysisResult | None = None
        for event in self.stream(message):
            if event.result is not None:
                result = event.result
        assert result is not None
        return result

    def stream(self, message: Message) -> Iterator[StageEvent]:
        t0 = time.perf_counter()
        ctx = PipelineContext(message=message, original_text=message.text.strip(), text=message.text.strip())
        stages: list[PipelineStage] = []

        for i, stage in enumerate(self._stages, start=1):
            yield StageEvent(PipelineStage(i, stage.name, stage.label, StageStatus.RUNNING))
            try:
                signal = stage.timed(ctx)
                ctx.signals.append(signal)
                done = PipelineStage(i, stage.name, stage.label, StageStatus.DONE, signal.latency_ms, signal)
            except Exception as exc:  # noqa: BLE001 — one failing model must not kill the verdict
                done = PipelineStage(i, stage.name, stage.label, StageStatus.FAILED, 0, None, f"{type(exc).__name__}: {exc}")
            stages.append(done)
            yield StageEvent(done)

        yield StageEvent(PipelineStage(FUSION_INDEX, "fusion", "Risk fusion", StageStatus.RUNNING))
        tf = time.perf_counter()
        verdict: Verdict = self._fusion.fuse(ctx.signals, ctx.entities)
        fusion_ms = int((time.perf_counter() - tf) * 1000)
        fusion_signal = Signal(
            "fusion",
            "Risk fusion",
            verdict.score / 100,
            0.0,
            f"{verdict.label.value} — weighted score {verdict.score}/100.",
            fusion_ms,
            {"confidence": verdict.confidence},
        )
        fusion_stage = PipelineStage(FUSION_INDEX, "fusion", "Risk fusion", StageStatus.DONE, fusion_ms, fusion_signal)
        stages.append(fusion_stage)

        result = AnalysisResult(
            message=message,
            language=ctx.language,
            language_confidence=ctx.language_confidence,
            translated_text=ctx.translated_text,
            entities=ctx.entities,
            signals=ctx.signals,
            stages=stages,
            verdict=verdict,
            total_latency_ms=int((time.perf_counter() - t0) * 1000),
        )
        yield StageEvent(fusion_stage, result)
