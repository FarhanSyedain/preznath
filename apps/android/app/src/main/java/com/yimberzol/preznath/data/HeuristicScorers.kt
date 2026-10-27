package com.yimberzol.preznath.data

/**
 * Honest offline stand-ins for the cloud BERT spam / phishing / intent stages.
 * Keyword and pattern scoring — not neural nets. Evidence labels say so.
 */
object HeuristicScorers {

    private data class IntentRule(val label: String, val risk: Double, val keywords: List<String>)

    private val spamHints = listOf(
        "congratulations", "won", "winner", "lottery", "click here", "verify now",
        "account blocked", "kyc", "cashback", "claim", "urgent", "limited time",
        "free gift", "selected", "reward", "act now", "suspended",
        "तुरंत", "काट दिया", "ब्लॉक",
    )
    private val phishingHints = listOf(
        "otp", "pin", "cvv", "password", "verify your", "confirm your",
        "update kyc", "click the link", "bit.ly", "tinyurl", "share the otp",
        "enter your pin", "card number", "net banking", "re-activate",
        "customer care", "executive",
        "कॉल करें", "अपडेट",
    )
    private val intentRules = listOf(
        IntentRule("request for OTP or PIN", 1.0, listOf("otp", "upi pin", "share pin", "enter pin", "cvv")),
        IntentRule("account blocked threat", 0.95, listOf("blocked", "suspended", "deactivated", "account will be")),
        IntentRule("lottery or prize", 0.95, listOf("lottery", "won", "prize", "lucky draw", "jackpot")),
        IntentRule("KYC or account verification", 0.9, listOf("kyc", "update pan", "re-activate", "verify account")),
        IntentRule(
            "electricity bill disconnection", 0.9,
            listOf(
                "electricity", "bijli", "disconnected", "connection will be cut", "power will be",
                "kaat diya", "बिजली", "कनेक्शन", "काट दिया", "बिल अपडेट",
            ),
        ),
        IntentRule("cashback or refund", 0.85, listOf("cashback", "refund", "claim before")),
        IntentRule("delivery or customs fee", 0.85, listOf("customs", "parcel", "courier", "redelivery fee")),
        IntentRule("job offer", 0.7, listOf("part time job", "work from home", "data entry", "registration fee")),
        IntentRule("request to send money", 0.6, listOf("send money", "transfer", "pay to this upi", "bhej")),
        IntentRule("legitimate bank notification", 0.1, listOf("debited from", "credited to", "upi ref", "avl bal")),
        IntentRule("normal personal message", 0.0, listOf("dinner", "movie", "birthday", "hostel", "thanks for lunch")),
    )

    fun spamProbability(text: String): Double {
        val t = text.lowercase()
        val hits = spamHints.count { it in t }
        return (hits * 0.18).coerceIn(0.05, 0.95)
    }

    fun phishingProbability(text: String): Double {
        val t = text.lowercase()
        val hits = phishingHints.count { it in t }
        var p = (hits * 0.16).coerceIn(0.05, 0.95)
        if (Regex("""https?://|www\.|bit\.ly""", RegexOption.IGNORE_CASE).containsMatchIn(text) &&
            Regex("""kyc|otp|pin|verify|blocked""", RegexOption.IGNORE_CASE).containsMatchIn(text)
        ) {
            p = maxOf(p, 0.75)
        }
        return p
    }

    fun intentScore(text: String): Triple<Double, String, Double> {
        val t = text.lowercase()
        var best = IntentRule("normal personal message", 0.0, emptyList())
        var bestHits = 0
        for (rule in intentRules) {
            val hits = rule.keywords.count { it in t }
            if (hits > bestHits || (hits == bestHits && hits > 0 && rule.risk > best.risk)) {
                if (hits > 0) {
                    bestHits = hits
                    best = rule
                }
            }
        }
        val conf = if (bestHits == 0) 0.4 else minOf(0.95, 0.45 + bestHits * 0.2)
        val score = if (bestHits == 0) 0.15 else best.risk * conf
        return Triple(score.coerceIn(0.0, 1.0), best.label, conf)
    }
}
