from __future__ import annotations

from satark.domain import AnalysisResult, Entity, PipelineStage, Signal, Verdict

from .schemas import AnalysisResultDTO, EntityDTO, SignalDTO, StageDTO, VerdictDTO


def entity_to_dto(e: Entity) -> EntityDTO:
    return EntityDTO(kind=e.kind.value, value=e.value, valid=e.valid, note=e.note)


def signal_to_dto(s: Signal) -> SignalDTO:
    return SignalDTO(
        name=s.name,
        label=s.label,
        score=round(s.clamped(), 4),
        weight=s.weight,
        evidence=s.evidence,
        latency_ms=s.latency_ms,
        details=s.details,
    )


def stage_to_dto(st: PipelineStage) -> StageDTO:
    return StageDTO(
        index=st.index,
        name=st.name,
        label=st.label,
        status=st.status.value,
        latency_ms=st.latency_ms,
        signal=signal_to_dto(st.signal) if st.signal else None,
        error=st.error,
    )


def verdict_to_dto(v: Verdict) -> VerdictDTO:
    return VerdictDTO(label=v.label.value, score=v.score, confidence=v.confidence, reasons=v.reasons, advice=v.advice)


def result_to_dto(r: AnalysisResult) -> AnalysisResultDTO:
    return AnalysisResultDTO(
        text=r.message.text,
        upi_id=r.message.upi_id,
        url=r.message.url,
        language=r.language,
        language_confidence=round(r.language_confidence, 3),
        translated_text=r.translated_text,
        entities=[entity_to_dto(e) for e in r.entities],
        signals=[signal_to_dto(s) for s in r.signals],
        stages=[stage_to_dto(s) for s in r.stages],
        verdict=verdict_to_dto(r.verdict),
        total_latency_ms=r.total_latency_ms,
    )
