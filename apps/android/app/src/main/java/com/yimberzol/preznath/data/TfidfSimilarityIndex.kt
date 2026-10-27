package com.yimberzol.preznath.data

import com.yimberzol.preznath.domain.CorpusEntry
import com.yimberzol.preznath.domain.SimilarityMatch
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Offline known-scam similarity via TF-IDF + cosine.
 * Always available; used when ONNX MiniLM is absent or fails to load.
 */
class TfidfSimilarityIndex(private val corpus: List<CorpusEntry>) {
    private val docs: List<Map<String, Double>>
    private val idf: Map<String, Double>
    val engineName: String = "tfidf"

    init {
        val tokenized = corpus.map { tokenize(it.text) }
        val df = mutableMapOf<String, Int>()
        for (tokens in tokenized) {
            for (t in tokens.toSet()) df[t] = (df[t] ?: 0) + 1
        }
        val n = corpus.size.coerceAtLeast(1).toDouble()
        idf = df.mapValues { (_, c) -> ln((n + 1) / (c + 1)) + 1.0 }
        docs = tokenized.map { tfidf(it) }
    }

    fun nearest(text: String, k: Int = 5): List<SimilarityMatch> {
        val q = tfidf(tokenize(text))
        return corpus.indices
            .map { i ->
                SimilarityMatch(
                    text = corpus[i].text,
                    category = corpus[i].category,
                    isScam = corpus[i].isScam,
                    similarity = cosine(q, docs[i]),
                )
            }
            .sortedByDescending { it.similarity }
            .take(k)
    }

    fun similaritySignal(text: String): Pair<Double, String> {
        val matches = nearest(text, 5)
        val scamSim = matches.filter { it.isScam }.maxOfOrNull { it.similarity } ?: 0.0
        val legitSim = matches.filter { !it.isScam }.maxOfOrNull { it.similarity } ?: 0.0
        val contrast = (scamSim - legitSim) * 2.5 + 0.5
        val strength = minOf(1.0, maxOf(scamSim, legitSim) / 0.6)
        val score = maxOf(0.0, minOf(1.0, contrast)) * strength + (1 - strength) * 0.25
        val nearest = matches.firstOrNull()
        val evidence = when {
            nearest == null -> "No corpus match."
            nearest.isScam -> "${(nearest.similarity * 100).toInt()}% similar to a known “${nearest.category}” scam template."
            else -> "Closest match is a legitimate “${nearest.category}” message (${(nearest.similarity * 100).toInt()}%)."
        }
        return score.coerceIn(0.0, 1.0) to evidence
    }

    private fun tokenize(text: String): List<String> =
        text.lowercase()
            .replace(Regex("""[^\p{L}\p{N}\s@.]"""), " ")
            .split(Regex("""\s+"""))
            .filter { it.length >= 2 }

    private fun tfidf(tokens: List<String>): Map<String, Double> {
        if (tokens.isEmpty()) return emptyMap()
        val tf = mutableMapOf<String, Int>()
        for (t in tokens) tf[t] = (tf[t] ?: 0) + 1
        val maxTf = tf.values.maxOrNull()!!.toDouble()
        return tf.mapValues { (term, c) -> (0.5 + 0.5 * c / maxTf) * (idf[term] ?: 1.0) }
    }

    private fun cosine(a: Map<String, Double>, b: Map<String, Double>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        var dot = 0.0
        val (small, large) = if (a.size <= b.size) a to b else b to a
        for ((k, v) in small) {
            val w = large[k] ?: continue
            dot += v * w
        }
        val na = sqrt(a.values.sumOf { it * it })
        val nb = sqrt(b.values.sumOf { it * it })
        if (na == 0.0 || nb == 0.0) return 0.0
        return (dot / (na * nb)).coerceIn(0.0, 1.0)
    }
}
