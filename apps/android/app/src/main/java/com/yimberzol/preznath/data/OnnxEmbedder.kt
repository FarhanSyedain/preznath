package com.yimberzol.preznath.data

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.LongBuffer
import kotlin.math.sqrt

/**
 * Optional on-device MiniLM embeddings via ONNX Runtime Mobile.
 * Requires model_qint8_arm64.onnx + vocab.txt under assets/models/.
 * Returns null from [create] when files or native ORT are unavailable — callers use TF-IDF.
 */
class OnnxEmbedder private constructor(
    private val env: OrtEnvironment,
    private val session: OrtSession,
    private val tokenizer: WordPieceTokenizer,
) : AutoCloseable {
    val dim: Int = 384

    fun embed(text: String): FloatArray {
        val encoded = tokenizer.encode(text, maxLen = 128)
        val shape = longArrayOf(1, encoded.inputIds.size.toLong())
        val inputIds = OnnxTensor.createTensor(env, LongBuffer.wrap(encoded.inputIds), shape)
        val attention = OnnxTensor.createTensor(env, LongBuffer.wrap(encoded.attentionMask), shape)
        val tokenTypes = OnnxTensor.createTensor(env, LongBuffer.wrap(encoded.tokenTypeIds), shape)
        inputIds.use { ids ->
            attention.use { mask ->
                tokenTypes.use { types ->
                    val feeds = mutableMapOf<String, OnnxTensor>(
                        "input_ids" to ids,
                        "attention_mask" to mask,
                    )
                    if (session.inputNames.contains("token_type_ids")) {
                        feeds["token_type_ids"] = types
                    }
                    session.run(feeds).use { results ->
                        val first = results[0].value
                        return meanPool(first, encoded.attentionMask)
                    }
                }
            }
        }
    }

    fun cosine(a: FloatArray, b: FloatArray): Double {
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        if (na == 0.0 || nb == 0.0) return 0.0
        return (dot / (sqrt(na) * sqrt(nb))).coerceIn(-1.0, 1.0)
    }

    override fun close() {
        session.close()
    }

    @Suppress("UNCHECKED_CAST")
    private fun meanPool(raw: Any, attentionMask: LongArray): FloatArray {
        // Output shapes vary: float[][][] [1,seq,384] or OnnxTensor already extracted.
        val seq: Array<FloatArray> = when (raw) {
            is Array<*> -> {
                val batch = raw as Array<Array<FloatArray>>
                batch[0]
            }
            else -> error("Unexpected ONNX output type: ${raw::class.java}")
        }
        val out = FloatArray(dim)
        var count = 0
        for (i in seq.indices) {
            if (attentionMask.getOrElse(i) { 0L } == 0L) continue
            val row = seq[i]
            for (j in 0 until dim) out[j] += row[j]
            count++
        }
        if (count == 0) return out
        for (j in 0 until dim) out[j] /= count
        var norm = 0.0
        for (v in out) norm += v * v
        norm = sqrt(norm)
        if (norm > 0) for (j in 0 until dim) out[j] = (out[j] / norm).toFloat()
        return out
    }

    companion object {
        fun create(modelFile: File, vocabFile: File): OnnxEmbedder? = try {
            if (!modelFile.exists() || !vocabFile.exists()) {
                null
            } else {
                val env = OrtEnvironment.getEnvironment()
                val opts = OrtSession.SessionOptions()
                val session = env.createSession(modelFile.absolutePath, opts)
                val tokenizer = WordPieceTokenizer.fromVocab(vocabFile.readText())
                OnnxEmbedder(env, session, tokenizer)
            }
        } catch (_: Throwable) {
            null
        }

        fun createFromBytes(modelBytes: ByteArray, vocabText: String): OnnxEmbedder? {
            return try {
                val env = OrtEnvironment.getEnvironment()
                val opts = OrtSession.SessionOptions()
                val session = env.createSession(modelBytes, opts)
                val tokenizer = WordPieceTokenizer.fromVocab(vocabText)
                OnnxEmbedder(env, session, tokenizer)
            } catch (_: Throwable) {
                null
            }
        }
    }
}

/** Minimal WordPiece tokenizer compatible with BERT / MiniLM vocab.txt. */
class WordPieceTokenizer(
    private val vocab: Map<String, Int>,
    private val unkId: Int = vocab["[UNK]"] ?: 100,
    private val clsId: Int = vocab["[CLS]"] ?: 101,
    private val sepId: Int = vocab["[SEP]"] ?: 102,
    private val padId: Int = vocab["[PAD]"] ?: 0,
) {
    data class Encoded(
        val inputIds: LongArray,
        val attentionMask: LongArray,
        val tokenTypeIds: LongArray,
    )

    fun encode(text: String, maxLen: Int = 128): Encoded {
        val tokens = mutableListOf(clsId)
        for (word in basicTokenize(text)) {
            tokens += wordPiece(word)
            if (tokens.size >= maxLen - 1) break
        }
        tokens += sepId
        val truncated = tokens.take(maxLen)
        val ids = LongArray(maxLen) { i -> if (i < truncated.size) truncated[i].toLong() else padId.toLong() }
        val mask = LongArray(maxLen) { i -> if (i < truncated.size) 1L else 0L }
        val types = LongArray(maxLen)
        return Encoded(ids, mask, types)
    }

    private fun basicTokenize(text: String): List<String> =
        text.lowercase()
            .replace(Regex("""([^\p{L}\p{N}])"""), " $1 ")
            .split(Regex("""\s+"""))
            .filter { it.isNotBlank() }

    private fun wordPiece(word: String): List<Int> {
        if (word in vocab) return listOf(vocab.getValue(word))
        val ids = mutableListOf<Int>()
        var start = 0
        while (start < word.length) {
            var end = word.length
            var cur: String? = null
            while (start < end) {
                val substr = if (start == 0) word.substring(start, end) else "##${word.substring(start, end)}"
                if (substr in vocab) {
                    cur = substr
                    break
                }
                end--
            }
            if (cur == null) return listOf(unkId)
            ids += vocab.getValue(cur)
            start = end
        }
        return ids
    }

    companion object {
        fun fromVocab(text: String): WordPieceTokenizer {
            val map = linkedMapOf<String, Int>()
            text.lineSequence().forEachIndexed { i, line ->
                val t = line.trim()
                if (t.isNotEmpty()) map[t] = i
            }
            return WordPieceTokenizer(map)
        }
    }
}
