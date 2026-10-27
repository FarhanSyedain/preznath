package com.yimberzol.preznath.application

import com.yimberzol.preznath.domain.AnalysisResult
import com.yimberzol.preznath.domain.AnalyzerPort
import com.yimberzol.preznath.domain.Message
import com.yimberzol.preznath.domain.PipelineStage
import com.yimberzol.preznath.domain.Signal
import com.yimberzol.preznath.domain.StageEvent
import com.yimberzol.preznath.domain.StageStatus
import com.yimberzol.preznath.domain.Verdict

/**
 * Orchestrates offline stages and risk fusion. Emits stage events for the live trace UI.
 */
class AnalyzeMessageUseCase(
    private val analyzer: AnalyzerPort,
) {
    fun execute(message: Message): AnalysisResult = analyzer.analyze(message)

    fun stream(message: Message): Sequence<StageEvent> = analyzer.stream(message)

    companion object {
        const val FUSION_INDEX = 8

        fun stagePlan(): List<PipelineStage> = listOf(
            PipelineStage(1, "language", "Normalize & language", StageStatus.PENDING),
            PipelineStage(2, "entities", "Entity extraction", StageStatus.PENDING),
            PipelineStage(3, "spam", "Spam heuristic", StageStatus.PENDING),
            PipelineStage(4, "phishing", "Phishing heuristic", StageStatus.PENDING),
            PipelineStage(5, "intent", "Scam intent", StageStatus.PENDING),
            PipelineStage(6, "similarity", "Known-scam similarity", StageStatus.PENDING),
            PipelineStage(7, "rules", "Rule signals", StageStatus.PENDING),
            PipelineStage(FUSION_INDEX, "fusion", "Risk fusion", StageStatus.PENDING),
        )

        fun fusionSignal(verdict: Verdict, latencyMs: Int): Signal = Signal(
            name = "fusion",
            label = "Risk fusion",
            score = verdict.score / 100.0,
            weight = 0.0,
            evidence = "${verdict.label.name} — weighted score ${verdict.score}/100.",
            latencyMs = latencyMs,
            details = mapOf("confidence" to verdict.confidence),
        )
    }
}
