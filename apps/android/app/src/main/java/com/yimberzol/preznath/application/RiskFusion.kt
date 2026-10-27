package com.yimberzol.preznath.application

import com.yimberzol.preznath.domain.Entity
import com.yimberzol.preznath.domain.EntityKind
import com.yimberzol.preznath.domain.Signal
import com.yimberzol.preznath.domain.Verdict
import com.yimberzol.preznath.domain.VerdictLabel

/**
 * Deterministic risk fusion — same thresholds as the FastAPI server.
 * <30 SAFE | 30..65 SUSPICIOUS | >65 FRAUD
 */
object RiskFusion {
    const val SAFE_BELOW = 30
    const val FRAUD_ABOVE = 65

    private val hardFloors = listOf(
        "lookalike" to 70,
        "OTP, PIN or CVV" to 70,
        "to receive money" to 70,
        "remote-access" to 65,
    )

    private val helplineAdvice = listOf(
        "If you already paid, call the national cybercrime helpline 1930 right away and file a report at cybercrime.gov.in. Reporting within an hour gives the best chance of freezing the money.",
    )

    fun fuse(signals: List<Signal>, entities: List<Entity>): Verdict {
        val weighted = signals.filter { it.weight > 0 }
        val totalW = weighted.sumOf { it.weight }.coerceAtLeast(1e-9)
        val raw = weighted.sumOf { it.clamped() * it.weight } / totalW
        var score = (raw * 100).toInt().coerceIn(0, 100)
        score = maxOf(score, hardFloor(signals, entities))

        val label = labelFor(score)
        return Verdict(
            label = label,
            score = score,
            confidence = confidence(score, signals),
            reasons = reasons(signals, entities, label),
            advice = advice(signals, entities, label),
        )
    }

    fun labelFor(score: Int): VerdictLabel = when {
        score < SAFE_BELOW -> VerdictLabel.SAFE
        score > FRAUD_ABOVE -> VerdictLabel.FRAUD
        else -> VerdictLabel.SUSPICIOUS
    }

    private fun hardFloor(signals: List<Signal>, entities: List<Entity>): Int {
        var floor = 0
        val rules = signals.firstOrNull { it.name == "rules" }
        @Suppress("UNCHECKED_CAST")
        val fired = ((rules?.details?.get("rules") as? List<*>)?.map { it.toString() } ?: emptyList()).toMutableList()
        for (e in entities) {
            if (e.kind == EntityKind.UPI_VPA && e.note?.contains("lookalike") == true) {
                fired += "lookalike"
            }
        }
        for ((needle, value) in hardFloors) {
            if (fired.any { it.contains(needle) }) floor = maxOf(floor, value)
        }
        return floor
    }

    private fun confidence(score: Int, signals: List<Signal>): Double {
        val dist = minOf(kotlin.math.abs(score - SAFE_BELOW), kotlin.math.abs(score - FRAUD_ABOVE))
        val boundary = minOf(1.0, dist / 20.0)
        val scored = signals.filter { it.weight > 0 }.map { it.clamped() }
        val agreement = if (scored.size >= 2) {
            val mean = scored.average()
            val variance = scored.map { (it - mean) * (it - mean) }.average()
            maxOf(0.0, 1.0 - variance * 4)
        } else {
            0.5
        }
        return ((0.4 + 0.35 * boundary + 0.25 * agreement) * 100).toInt() / 100.0
    }

    private fun reasons(signals: List<Signal>, entities: List<Entity>, label: VerdictLabel): List<String> {
        val out = mutableListOf<String>()
        val rules = signals.firstOrNull { it.name == "rules" }
        @Suppress("UNCHECKED_CAST")
        val fired = (rules?.details?.get("rules") as? List<*>)?.map { it.toString() }.orEmpty()
        out += fired
        for (e in entities) {
            if (e.kind == EntityKind.UPI_VPA && e.valid == false) {
                val handle = e.value.substringAfterLast('@')
                out += "The UPI ID ${e.value} ends in “@$handle”, which ${e.note ?: "no bank or PSP uses"}."
            }
        }
        val ranked = signals
            .filter { it.weight > 0 && it.name != "rules" }
            .sortedByDescending { it.clamped() * it.weight }
        for (s in ranked) {
            if (label == VerdictLabel.SAFE) {
                if (s.clamped() < 0.35) out += s.evidence
            } else if (s.clamped() >= 0.5) {
                out += s.evidence
            }
        }
        return out.distinct().take(6)
    }

    private fun advice(signals: List<Signal>, entities: List<Entity>, label: VerdictLabel): List<String> {
        if (label == VerdictLabel.SAFE) {
            return listOf(
                "Nothing here looks like a scam pattern. Still confirm the sender if money is involved.",
                "Never share an OTP or UPI PIN with anyone, even if the message itself is genuine.",
            )
        }
        val rules = signals.firstOrNull { it.name == "rules" }
        @Suppress("UNCHECKED_CAST")
        val fired = (rules?.details?.get("rules") as? List<*>)?.joinToString(" ").orEmpty()
        val out = mutableListOf<String>()
        if ("to receive money" in fired) {
            out += "Do not approve any “collect” request — receiving money never requires approving a request or entering your PIN."
        }
        if ("OTP, PIN or CVV" in fired) {
            out += "Do not share the OTP, PIN or CVV. No bank, NPCI or payment app will ever ask for them."
        }
        if ("remote-access" in fired) {
            out += "Do not install AnyDesk, TeamViewer or any screen-sharing app on request. It gives the caller control of your phone."
        }
        if (entities.any { it.kind == EntityKind.URL }) {
            out += "Do not open the link. Open your bank's official app or type its website address yourself."
        }
        if (entities.any { it.kind == EntityKind.UPI_VPA && it.valid == false }) {
            out += "Do not send money to this UPI ID. Verify the payee name inside your UPI app before paying anyone."
        }
        if ("time pressure" in fired || "blocked" in fired) {
            out += "Ignore the deadline. Banks do not block accounts over SMS; call the number printed on your card to check."
        }
        if ("prize" in fired || "lottery" in fired.lowercase()) {
            out += "You cannot win a lottery you did not enter. Any fee asked to release a prize is the scam."
        }
        if (label == VerdictLabel.SUSPICIOUS && out.isEmpty()) {
            out += "Treat this with care. Confirm through a channel you already trust before sending money or tapping links."
        }
        out += "Report the sender to your bank and forward the message to 1930 or cybercrime.gov.in so others are warned."
        out += helplineAdvice
        return out.distinct().take(6)
    }
}
