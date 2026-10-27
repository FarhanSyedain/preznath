package com.yimberzol.preznath.domain

/** Port for message analysis. Offline and cloud implementations both satisfy this. */
interface AnalyzerPort {
    fun analyze(message: Message): AnalysisResult

    /** Stage-by-stage stream for the live pipeline trace UI. */
    fun stream(message: Message): Sequence<StageEvent>
}

data class StageEvent(
    val stage: PipelineStage,
    val result: AnalysisResult? = null,
)
