package com.yimberzol.preznath.data

/**
 * Offline language heuristic: Devanagari script detection + simple Hinglish cues.
 * Does not call a network translator — leaves Hindi text as-is for downstream stages
 * that already include Hindi scam templates.
 */
object LanguageNormalizer {
    private val DEVANAGARI = Regex("""[\u0900-\u097F]""")
    private val hinglishHints = listOf(
        "hai", "nahi", "karo", "karein", "bhej", "paise", "turant", "abhi", "jaldi", "aapko",
    )

    data class Result(
        val language: String,
        val confidence: Double,
        val normalizedText: String,
        val evidence: String,
    )

    fun normalize(raw: String): Result {
        val text = raw.replace(Regex("""[ \t]+"""), " ").trim()
        val devRatio = if (text.isEmpty()) 0.0
        else DEVANAGARI.findAll(text).count().toDouble() / text.length

        return when {
            devRatio > 0.25 -> Result(
                language = "hi",
                confidence = maxOf(0.95, devRatio),
                normalizedText = text,
                evidence = "Detected Hindi via Devanagari script. Passing text through for offline stages.",
            )
            hinglishHints.count { Regex("""\b${Regex.escape(it)}\b""", RegexOption.IGNORE_CASE).containsMatchIn(text) } >= 2 ->
                Result(
                    language = "hi-Latn",
                    confidence = 0.7,
                    normalizedText = text,
                    evidence = "Detected Hinglish (Latin script). Passing text through unchanged.",
                )
            else -> Result(
                language = "en",
                confidence = 0.8,
                normalizedText = text,
                evidence = "Detected English / mixed Latin text.",
            )
        }
    }
}
