package com.yimberzol.preznath.domain

enum class VerdictLabel { SAFE, SUSPICIOUS, FRAUD }

enum class EntityKind {
    UPI_VPA, PHONE, URL, AMOUNT, CREDENTIAL, BANK, APP
}

enum class StageStatus { PENDING, RUNNING, DONE, SKIPPED, FAILED }

data class Message(
    val text: String,
    val upiId: String? = null,
    val url: String? = null,
) {
    fun combinedText(): String = listOfNotNull(text, upiId, url).joinToString("\n")
}

data class Entity(
    val kind: EntityKind,
    val value: String,
    val valid: Boolean? = null,
    val note: String? = null,
)

data class Signal(
    val name: String,
    val label: String,
    val score: Double,
    val weight: Double,
    val evidence: String,
    val latencyMs: Int = 0,
    val details: Map<String, Any?> = emptyMap(),
) {
    fun clamped(): Double = score.coerceIn(0.0, 1.0)
}

data class Verdict(
    val label: VerdictLabel,
    val score: Int,
    val confidence: Double,
    val reasons: List<String>,
    val advice: List<String>,
)

data class PipelineStage(
    val index: Int,
    val name: String,
    val label: String,
    val status: StageStatus,
    val latencyMs: Int = 0,
    val signal: Signal? = null,
    val error: String? = null,
)

data class AnalysisResult(
    val message: Message,
    val language: String,
    val languageConfidence: Double,
    val translatedText: String?,
    val entities: List<Entity>,
    val signals: List<Signal>,
    val stages: List<PipelineStage>,
    val verdict: Verdict,
    val totalLatencyMs: Int,
    val engine: String = "offline",
)

data class CorpusEntry(
    val text: String,
    val category: String,
    val isScam: Boolean,
)

data class SimilarityMatch(
    val text: String,
    val category: String,
    val isScam: Boolean,
    val similarity: Double,
)
