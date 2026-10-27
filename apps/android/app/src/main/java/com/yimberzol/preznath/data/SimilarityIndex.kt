package com.yimberzol.preznath.data

import com.yimberzol.preznath.domain.CorpusEntry
import com.yimberzol.preznath.domain.SimilarityMatch
import com.yimberzol.preznath.domain.Signal

/**
 * Hybrid similarity: prefers ONNX MiniLM embeddings when available,
 * otherwise TF-IDF + cosine over the curated scam/legit corpus.
 */
class SimilarityIndex(
    private val corpus: List<CorpusEntry>,
    private val embedder: OnnxEmbedder? = null,
) {
    private val tfidf = TfidfSimilarityIndex(corpus)
    @Volatile private var corpusVectors: List<FloatArray>? = null

    val engineName: String = if (embedder != null) "onnx-minilm" else "tfidf"

    fun nearest(text: String, k: Int = 5): List<SimilarityMatch> {
        val emb = embedder ?: return tfidf.nearest(text, k)
        val vectors = synchronized(this) {
            corpusVectors ?: corpus.map { emb.embed(it.text) }.also { corpusVectors = it }
        }
        return try {
            val q = emb.embed(text)
            corpus.indices
                .map { i ->
                    SimilarityMatch(
                        text = corpus[i].text,
                        category = corpus[i].category,
                        isScam = corpus[i].isScam,
                        similarity = emb.cosine(q, vectors[i]).coerceIn(0.0, 1.0),
                    )
                }
                .sortedByDescending { it.similarity }
                .take(k)
        } catch (_: Throwable) {
            tfidf.nearest(text, k)
        }
    }

    fun signal(text: String): Signal {
        val matches = nearest(text, 5)
        val scamSim = matches.filter { it.isScam }.maxOfOrNull { it.similarity } ?: 0.0
        val legitSim = matches.filter { !it.isScam }.maxOfOrNull { it.similarity } ?: 0.0
        val contrast = (scamSim - legitSim) * 2.5 + 0.5
        val strength = minOf(1.0, maxOf(scamSim, legitSim) / 0.6)
        val score = maxOf(0.0, minOf(1.0, contrast)) * strength + (1 - strength) * 0.25
        val nearest = matches.firstOrNull()
        val evidence = when {
            nearest == null -> "No corpus match ($engineName)."
            nearest.isScam ->
                "${(nearest.similarity * 100).toInt()}% similar to a known “${nearest.category}” scam template ($engineName)."
            else ->
                "Closest match is a legitimate “${nearest.category}” message (${(nearest.similarity * 100).toInt()}%) ($engineName)."
        }
        return Signal(
            name = "similarity",
            label = "Known-scam similarity",
            score = score.coerceIn(0.0, 1.0),
            weight = 0.20,
            evidence = evidence,
            details = mapOf(
                "engine" to engineName,
                "nearest" to matches.take(3).map {
                    mapOf(
                        "category" to it.category,
                        "is_scam" to it.isScam,
                        "similarity" to ((it.similarity * 10000).toInt() / 10000.0),
                    )
                },
            ),
        )
    }
}
