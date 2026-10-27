package com.yimberzol.preznath.data

import android.content.Context
import com.yimberzol.preznath.domain.AnalyzerPort
import java.io.File

object AnalyzerFactory {
    @Volatile
    private var cached: OfflineAnalyzer? = null

    fun offline(context: Context, stageDelayMs: Long = 120L): OfflineAnalyzer {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val corpus = loadCorpus(context)
            val embedder = tryLoadOnnx(context)
            val similarity = SimilarityIndex(corpus, embedder)
            return OfflineAnalyzer(
                similarity = similarity,
                stageDelayMs = stageDelayMs,
            ).also { cached = it }
        }
    }

    fun cloud(baseUrl: String = CloudAnalyzer.DEFAULT_URL): AnalyzerPort = CloudAnalyzer(baseUrl)

    private fun loadCorpus(context: Context) = try {
        context.assets.open("scam_corpus.json").bufferedReader().use { CorpusLoader.fromJson(it.readText()) }
    } catch (_: Exception) {
        CorpusLoader.builtin()
    }

    private fun tryLoadOnnx(context: Context): OnnxEmbedder? = try {
        val modelName = "models/model_qint8_arm64.onnx"
        val vocabName = "models/vocab.txt"
        val dir = File(context.filesDir, "models").apply { mkdirs() }
        val modelFile = File(dir, "model_qint8_arm64.onnx")
        val vocabFile = File(dir, "vocab.txt")
        if (!modelFile.exists()) {
            context.assets.open(modelName).use { input ->
                modelFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        if (!vocabFile.exists()) {
            context.assets.open(vocabName).use { input ->
                vocabFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        OnnxEmbedder.create(modelFile, vocabFile)
    } catch (_: Throwable) {
        null
    }
}
