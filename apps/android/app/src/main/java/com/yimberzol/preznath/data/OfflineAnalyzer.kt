package com.yimberzol.preznath.data

import com.yimberzol.preznath.application.AnalyzeMessageUseCase
import com.yimberzol.preznath.application.RiskFusion
import com.yimberzol.preznath.domain.AnalysisResult
import com.yimberzol.preznath.domain.AnalyzerPort
import com.yimberzol.preznath.domain.Entity
import com.yimberzol.preznath.domain.EntityKind
import com.yimberzol.preznath.domain.Message
import com.yimberzol.preznath.domain.PipelineStage
import com.yimberzol.preznath.domain.Signal
import com.yimberzol.preznath.domain.StageEvent
import com.yimberzol.preznath.domain.StageStatus
import kotlin.system.measureTimeMillis

/**
 * Fully offline analyzer. No network. Stages mirror the FastAPI pipeline layout
 * so the live trace UI stays consistent with the web demo.
 */
class OfflineAnalyzer(
    private val extractor: EntityExtractor = EntityExtractor(),
    private val rules: RuleEngine = RuleEngine(),
    private val similarity: SimilarityIndex = SimilarityIndex(CorpusLoader.builtin()),
    private val stageDelayMs: Long = 0L,
) : AnalyzerPort {

    val similarityEngine: String get() = similarity.engineName

    override fun analyze(message: Message): AnalysisResult {
        var result: AnalysisResult? = null
        for (event in stream(message)) {
            if (event.result != null) result = event.result
        }
        return requireNotNull(result)
    }

    override fun stream(message: Message): Sequence<StageEvent> = sequence {
        val t0 = System.nanoTime()
        val original = message.text.trim()
        val signals = mutableListOf<Signal>()
        val stages = mutableListOf<PipelineStage>()
        var language = "en"
        var languageConfidence = 0.0
        var working = original
        var entities: List<Entity> = emptyList()

        fun sleepIfNeeded() {
            if (stageDelayMs > 0) Thread.sleep(stageDelayMs)
        }

        // 01 language
        yield(StageEvent(PipelineStage(1, "language", "Normalize & language", StageStatus.RUNNING)))
        sleepIfNeeded()
        lateinit var langSignal: Signal
        val langMs = measureTimeMillis {
            val lang = LanguageNormalizer.normalize(original)
            language = lang.language
            languageConfidence = lang.confidence
            working = lang.normalizedText
            langSignal = Signal(
                name = "language",
                label = "Normalize & language",
                score = 0.0,
                weight = 0.0,
                evidence = lang.evidence,
                details = mapOf("language" to language, "confidence" to languageConfidence),
            )
        }.toInt()
        val langDone = PipelineStage(1, "language", "Normalize & language", StageStatus.DONE, langMs, langSignal.copy(latencyMs = langMs))
        stages += langDone
        signals += langDone.signal!!
        yield(StageEvent(langDone))

        // 02 entities
        yield(StageEvent(PipelineStage(2, "entities", "Entity extraction", StageStatus.RUNNING)))
        sleepIfNeeded()
        lateinit var entSignal: Signal
        val entMs = measureTimeMillis {
            val source = listOfNotNull(original, working, message.upiId, message.url).joinToString("\n")
            entities = extractor.extract(source)
            entSignal = entitySignal(entities)
        }.toInt()
        val entDone = PipelineStage(2, "entities", "Entity extraction", StageStatus.DONE, entMs, entSignal.copy(latencyMs = entMs))
        stages += entDone
        signals += entDone.signal!!
        yield(StageEvent(entDone))

        // 03 spam heuristic
        yield(StageEvent(PipelineStage(3, "spam", "Spam heuristic", StageStatus.RUNNING)))
        sleepIfNeeded()
        val spamP = HeuristicScorers.spamProbability(working)
        val spamEvidence = when {
            spamP >= 0.7 -> "Offline spam heuristic rates this ${(spamP * 100).toInt()}% spam-like."
            spamP >= 0.4 -> "Offline spam heuristic is unsure (${(spamP * 100).toInt()}%)."
            else -> "Offline spam heuristic rates this ${(spamP * 100).toInt()}% spam — reads like a normal message."
        }
        val spamDone = PipelineStage(
            3, "spam", "Spam heuristic", StageStatus.DONE, stageDelayMs.toInt(),
            Signal("spam", "Spam heuristic", spamP, 0.10, spamEvidence, stageDelayMs.toInt()),
        )
        stages += spamDone
        signals += spamDone.signal!!
        yield(StageEvent(spamDone))

        // 04 phishing heuristic
        yield(StageEvent(PipelineStage(4, "phishing", "Phishing heuristic", StageStatus.RUNNING)))
        sleepIfNeeded()
        val phishP = HeuristicScorers.phishingProbability(message.combinedText())
        val phishEvidence = when {
            phishP >= 0.7 -> "Offline phishing heuristic rates this ${(phishP * 100).toInt()}% phishing-like."
            phishP >= 0.4 -> "Offline phishing heuristic is unsure (${(phishP * 100).toInt()}%)."
            else -> "Offline phishing heuristic rates this ${(phishP * 100).toInt()}% — no credential-harvesting pattern."
        }
        val phishDone = PipelineStage(
            4, "phishing", "Phishing heuristic", StageStatus.DONE, stageDelayMs.toInt(),
            Signal("phishing", "Phishing heuristic", phishP, 0.20, phishEvidence, stageDelayMs.toInt()),
        )
        stages += phishDone
        signals += phishDone.signal!!
        yield(StageEvent(phishDone))

        // 05 intent
        yield(StageEvent(PipelineStage(5, "intent", "Scam intent", StageStatus.RUNNING)))
        sleepIfNeeded()
        val (intentScore, intentLabel, intentConf) = HeuristicScorers.intentScore(working)
        val intentEvidence = if (intentScore >= 0.5) {
            "Reads as “$intentLabel” (${(intentConf * 100).toInt()}%), a common scam pretext (offline heuristic)."
        } else {
            "Reads as “$intentLabel” (${(intentConf * 100).toInt()}%) (offline heuristic)."
        }
        val intentDone = PipelineStage(
            5, "intent", "Scam intent", StageStatus.DONE, stageDelayMs.toInt(),
            Signal(
                "intent", "Scam intent", intentScore, 0.20, intentEvidence, stageDelayMs.toInt(),
                mapOf("label" to intentLabel, "confidence" to intentConf),
            ),
        )
        stages += intentDone
        signals += intentDone.signal!!
        yield(StageEvent(intentDone))

        // 06 similarity
        yield(StageEvent(PipelineStage(6, "similarity", "Known-scam similarity", StageStatus.RUNNING)))
        sleepIfNeeded()
        lateinit var simSignal: Signal
        val simMs = measureTimeMillis {
            simSignal = similarity.signal(working)
        }.toInt()
        val simDone = PipelineStage(6, "similarity", "Known-scam similarity", StageStatus.DONE, simMs, simSignal.copy(latencyMs = simMs))
        stages += simDone
        signals += simDone.signal!!
        yield(StageEvent(simDone))

        // 07 rules
        yield(StageEvent(PipelineStage(7, "rules", "Rule signals", StageStatus.RUNNING)))
        sleepIfNeeded()
        lateinit var ruleSignal: Signal
        val ruleMs = measureTimeMillis {
            ruleSignal = rules.evaluate(original, working, entities)
        }.toInt()
        val ruleDone = PipelineStage(7, "rules", "Rule signals", StageStatus.DONE, ruleMs, ruleSignal.copy(latencyMs = ruleMs))
        stages += ruleDone
        signals += ruleDone.signal!!
        yield(StageEvent(ruleDone))

        // 08 fusion
        yield(StageEvent(PipelineStage(AnalyzeMessageUseCase.FUSION_INDEX, "fusion", "Risk fusion", StageStatus.RUNNING)))
        sleepIfNeeded()
        val verdict = RiskFusion.fuse(signals, entities)
        val fusionMs = 1
        val fusionSignal = AnalyzeMessageUseCase.fusionSignal(verdict, fusionMs)
        val fusionStage = PipelineStage(
            AnalyzeMessageUseCase.FUSION_INDEX, "fusion", "Risk fusion",
            StageStatus.DONE, fusionMs, fusionSignal,
        )
        stages += fusionStage
        val totalMs = ((System.nanoTime() - t0) / 1_000_000L).toInt()
        val result = AnalysisResult(
            message = message,
            language = language,
            languageConfidence = languageConfidence,
            translatedText = null,
            entities = entities,
            signals = signals,
            stages = stages,
            verdict = verdict,
            totalLatencyMs = totalMs,
            engine = "offline/${similarity.engineName}",
        )
        yield(StageEvent(fusionStage, result))
    }

    private fun entitySignal(entities: List<Entity>): Signal {
        var score = 0.0
        val notes = mutableListOf<String>()
        for (e in entities) {
            when (e.kind) {
                EntityKind.UPI_VPA -> when {
                    e.valid == false && e.note?.contains("lookalike") == true -> {
                        score = maxOf(score, 0.95)
                        notes += "UPI ID ${e.value} uses a lookalike handle (${e.note})."
                    }
                    e.valid == false -> {
                        score = maxOf(score, 0.6)
                        notes += "UPI ID ${e.value} ends in a handle no bank or PSP uses."
                    }
                }
                EntityKind.URL -> when {
                    e.note?.contains("shortened") == true -> {
                        score = maxOf(score, 0.55)
                        notes += "Shortened link ${e.value} hides its real destination."
                    }
                    e.note?.contains("apk") == true -> {
                        score = maxOf(score, 0.9)
                        notes += "Link ${e.value} points to an APK file."
                    }
                    e.valid == false -> {
                        score = maxOf(score, 0.5)
                        notes += "Link ${e.value} is not on a known bank or PSP domain."
                    }
                }
                EntityKind.CREDENTIAL -> score = maxOf(score, 0.5)
                EntityKind.APP -> {
                    score = maxOf(score, 0.8)
                    notes += "Mentions remote-access app ${e.value}."
                }
                else -> Unit
            }
        }
        val evidence = when {
            entities.isEmpty() -> "No UPI IDs, links, phone numbers or amounts found."
            notes.isEmpty() -> {
                val kinds = entities.map { it.kind.name.lowercase().replace('_', ' ') }.toSortedSet()
                "Found ${entities.size} entities (${kinds.joinToString()}); nothing unusual about them."
            }
            else -> notes.take(2).joinToString(" ")
        }
        return Signal(
            name = "entities",
            label = "Entity extraction",
            score = score,
            weight = 0.15,
            evidence = evidence,
            details = mapOf("count" to entities.size),
        )
    }
}
