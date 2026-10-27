package com.yimberzol.preznath.data

import com.yimberzol.preznath.domain.Entity
import com.yimberzol.preznath.domain.EntityKind
import com.yimberzol.preznath.domain.Signal

/** Hand-written UPI fraud red flags. Weight 0.15 in fusion. */
class RuleEngine {
    fun evaluate(originalText: String, workingText: String, entities: List<Entity>): Signal {
        val text = "$originalText\n$workingText"
        val fired = mutableListOf<Pair<String, Double>>()

        if (CREDENTIAL_ASK.containsMatchIn(text)) {
            fired += "Asks you to share or enter an OTP, PIN or CVV." to 1.0
        }
        if (COLLECT_TRICK.containsMatchIn(text)) {
            fired += "Asks you to approve a request or enter a PIN to receive money — receiving money never needs that." to 1.0
        }
        if (REMOTE_APP.containsMatchIn(text)) {
            fired += "Asks you to install a remote-access app such as AnyDesk or TeamViewer." to 0.95
        }
        if (APK.containsMatchIn(text)) {
            fired += "Pushes you to install an app or APK from a link." to 0.8
        }
        if (SHORTENER.containsMatchIn(text)) {
            fired += "Uses a shortened link that hides the real website." to 0.6
        }
        if (URGENCY.containsMatchIn(text) || HINDI_URGENCY.containsMatchIn(text)) {
            fired += "Creates time pressure (“immediately”, “today”, “will be blocked”)." to 0.6
        }
        if (HINDI_ELECTRICITY.containsMatchIn(text)) {
            fired += "Threatens electricity disconnection unless you call or pay immediately." to 0.7
        }
        if (PRIZE.containsMatchIn(text)) {
            fired += "Promises a prize, reward or lottery win." to 0.7
        }
        if (THREAT.containsMatchIn(text)) {
            fired += "Uses a threat or emergency (police, customs, accident) to rush you." to 0.6
        }

        val claimed = BANK_WORDS.filter { (word, _) ->
            Regex("""\b${Regex.escape(word)}\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)
        }.values.toSet()
        for (v in entities.filter { it.kind == EntityKind.UPI_VPA }) {
            val handle = v.value.substringAfterLast('@').lowercase()
            val handleBank = HANDLE_BANK[handle]
            if (claimed.isNotEmpty() && handleBank != null && handleBank !in claimed &&
                claimed.intersect(setOf("paytm", "phonepe", "gpay")).isEmpty()
            ) {
                fired += "Claims to be from ${claimed.sorted().joinToString(", ").uppercase()} but the UPI ID ${v.value} belongs to another bank." to 0.8
            }
        }

        val score: Double
        val evidence: String
        if (fired.isEmpty()) {
            score = 0.0
            evidence = "No urgency, credential request, remote-app or prize pattern."
        } else {
            val top = fired.maxOf { it.second }
            score = minOf(1.0, top + 0.1 * (fired.size - 1))
            evidence = fired.first().first
        }

        return Signal(
            name = "rules",
            label = "Rule signals",
            score = score,
            weight = 0.15,
            evidence = evidence,
            details = mapOf("rules" to fired.map { it.first }),
        )
    }

    companion object {
        private val URGENCY = Regex(
            """\b(immediately|urgent(ly)?|today|within \d+ (hours?|hrs?|minutes?|mins?)|last (chance|warning)|right now|asap|expire[sd]?|will be (blocked|suspended|deactivated|disconnected|closed)|final notice|act now|turant|abhi|jaldi)\b""",
            RegexOption.IGNORE_CASE,
        )
        private val COLLECT_TRICK = Regex(
            """(collect request|approve (the |this )?(request|payment)|accept (the |this )?(request|payment) to receive|enter (your )?(upi )?pin to receive|pay request|request money sent|approve .{0,20}to (get|receive))""",
            RegexOption.IGNORE_CASE,
        )
        private val CREDENTIAL_ASK = Regex(
            """(share|send|tell|enter|provide|verify|confirm|type)\b.{0,40}\b(otp|pin|cvv|password|mpin|upi pin|card number|atm pin)|\b(otp|pin|cvv|mpin)\b.{0,30}\b(share|send|reply|batao|bhejo)""",
            RegexOption.IGNORE_CASE,
        )
        private val REMOTE_APP = Regex(
            """\b(anydesk|teamviewer|quick ?support|rustdesk|airdroid|screen ?share)\b""",
            RegexOption.IGNORE_CASE,
        )
        private val APK = Regex(
            """\.apk\b|install (the |this )?(app|application)|download (the |this )?(app|application)""",
            RegexOption.IGNORE_CASE,
        )
        private val SHORTENER = Regex(
            """\b(bit\.ly|tinyurl\.com|t\.co|goo\.gl|cutt\.ly|rb\.gy|is\.gd|shorturl\.at|tiny\.cc|rebrand\.ly|ow\.ly)\b""",
            RegexOption.IGNORE_CASE,
        )
        private val PRIZE = Regex(
            """\b(won|winner|lottery|lucky draw|prize|jackpot|congratulations|reward)\b""",
            RegexOption.IGNORE_CASE,
        )
        private val THREAT = Regex(
            """\b(police|arrest|legal action|fir|court|penalty|fine|customs|seized|accident|hospital|emergency)\b""",
            RegexOption.IGNORE_CASE,
        )
        private val HINDI_URGENCY = Regex("""तुरंत|अभी|जल्दी|आज रात|आज ही|काट दिया जाएगा|ब्लॉक""")
        private val HINDI_ELECTRICITY = Regex("""बिजली|बिजली कनेक्शन|बिजली बिल|काट दिया जाएगा""")

        private val BANK_WORDS = mapOf(
            "sbi" to "sbi", "state bank" to "sbi", "yono" to "sbi",
            "hdfc" to "hdfc", "icici" to "icici", "axis" to "axis",
            "kotak" to "kotak", "pnb" to "pnb", "punjab national" to "pnb",
            "bank of baroda" to "bob", "bob" to "bob", "canara" to "canara",
            "union bank" to "union", "paytm" to "paytm", "phonepe" to "phonepe",
            "google pay" to "gpay", "gpay" to "gpay",
        )
        private val HANDLE_BANK = mapOf(
            "oksbi" to "sbi", "sbi" to "sbi",
            "okhdfcbank" to "hdfc", "hdfcbank" to "hdfc", "wahdfcbank" to "hdfc",
            "okicici" to "icici", "icici" to "icici", "waicici" to "icici",
            "okaxis" to "axis", "axisbank" to "axis", "jupiteraxis" to "axis", "waaxis" to "axis",
            "kotak" to "kotak", "kmbl" to "kotak",
            "pnb" to "pnb", "barodampay" to "bob", "cnrb" to "canara", "uboi" to "union",
            "paytm" to "paytm", "ptyes" to "paytm", "ptsbi" to "paytm", "ptaxis" to "paytm", "pthdfc" to "paytm",
            "ybl" to "phonepe", "ibl" to "phonepe", "axl" to "phonepe",
        )
    }
}
