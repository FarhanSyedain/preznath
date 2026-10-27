package com.yimberzol.preznath.data

import com.yimberzol.preznath.domain.Entity
import com.yimberzol.preznath.domain.EntityKind

/** Regex entity extraction tuned for Indian payment messages. */
class EntityExtractor(private val registry: HandleRegistry = HandleRegistry()) {

    fun extract(text: String): List<Entity> {
        val out = mutableListOf<Entity>()
        val seen = mutableSetOf<Pair<EntityKind, String>>()

        fun add(e: Entity) {
            val key = e.kind to e.value.lowercase()
            if (seen.add(key)) out += e
        }

        for (m in VPA_RE.findAll(text)) {
            val user = m.groupValues[1]
            val handle = m.groupValues[2]
            val after = text.getOrNull(m.range.last + 1)
            if (after == '.' || handle.lowercase() in EMAIL_TLDS) continue
            val vpa = "$user@$handle"
            when {
                registry.isKnown(handle) ->
                    add(Entity(EntityKind.UPI_VPA, vpa, true, registry.bankFor(handle)))
                else -> {
                    val look = registry.isLookalike(handle)
                    if (look != null) {
                        add(Entity(EntityKind.UPI_VPA, vpa, false, "lookalike of @${look.first}"))
                    } else {
                        add(Entity(EntityKind.UPI_VPA, vpa, false, "unknown handle"))
                    }
                }
            }
        }

        for (m in URL_RE.findAll(text)) {
            val url = m.groupValues[1].trimEnd('.', ',', ';', ':', ')')
            if ('@' in url) continue
            val dom = domain(url)
            when {
                dom in SHORTENERS || SHORTENERS.any { dom.endsWith(".$it") } ->
                    add(Entity(EntityKind.URL, url, false, "shortened link"))
                url.lowercase().endsWith(".apk") ->
                    add(Entity(EntityKind.URL, url, false, "apk download"))
                dom in TRUSTED_DOMAINS || TRUSTED_DOMAINS.any { dom.endsWith(".$it") } ->
                    add(Entity(EntityKind.URL, url, true, "known domain"))
                else -> add(Entity(EntityKind.URL, url, false, "unknown domain"))
            }
        }

        for (m in PHONE_RE.findAll(text)) {
            val digits = m.groupValues[1].filter { it.isDigit() }
            if (digits.length == 10) add(Entity(EntityKind.PHONE, digits))
        }

        for (m in AMOUNT_RE.findAll(text)) {
            val val1 = m.groupValues.getOrNull(1).orEmpty()
            val val2 = m.groupValues.getOrNull(2).orEmpty()
            val amount = val1.ifBlank { val2 }
            if (amount.isNotBlank()) add(Entity(EntityKind.AMOUNT, "₹$amount"))
        }

        for (m in CREDENTIAL_RE.findAll(text)) {
            add(Entity(EntityKind.CREDENTIAL, m.groupValues[1].uppercase()))
        }
        for (m in BANK_RE.findAll(text)) {
            add(Entity(EntityKind.BANK, m.groupValues[1]))
        }
        for (m in APP_RE.findAll(text)) {
            add(Entity(EntityKind.APP, m.groupValues[1]))
        }
        return out
    }

    private fun domain(url: String): String {
        var u = url.replace(Regex("^https?://", RegexOption.IGNORE_CASE), "")
        u = u.replace(Regex("^www\\.", RegexOption.IGNORE_CASE), "")
        return u.substringBefore('/').substringBefore('?').lowercase()
    }

    companion object {
        private val VPA_RE =
            Regex("""(?<![\w.])([A-Za-z0-9][\w.\-]{1,60})@([a-zA-Z][a-zA-Z0-9]{1,30})\b""")
        private val PHONE_RE =
            Regex("""(?<!\d)(?:\+?91[\s-]?|0)?([6-9]\d{4}[\s-]?\d{5})(?!\d)""")
        private val URL_RE = Regex(
            """\b((?:https?://|www\.)[^\s<>"']+|(?:[a-z0-9-]+\.)+(?:com|in|net|org|co|ly|gl|xyz|info|site|online|top|live|app|me|cc|io|link|club|vip|shop)(?:/[^\s<>"']*)?)""",
            RegexOption.IGNORE_CASE,
        )
        private val AMOUNT_RE = Regex(
            """(?:₹|Rs\.?|INR)\s?([\d,]+(?:\.\d{1,2})?)|\b([\d,]+(?:\.\d{1,2})?)\s?(?:rupees|rs|inr)\b""",
            RegexOption.IGNORE_CASE,
        )
        private val CREDENTIAL_RE =
            Regex("""\b(OTP|UPI PIN|MPIN|PIN|CVV|password|ATM PIN|card number)\b""", RegexOption.IGNORE_CASE)
        private val BANK_RE = Regex(
            """\b(SBI|State Bank|YONO|HDFC|ICICI|Axis|Kotak|PNB|Punjab National|Bank of Baroda|BOB|Canara|Union Bank|IndusInd|IDFC|Yes Bank|Federal|RBL|Paytm|PhonePe|Google Pay|GPay|BHIM|NPCI|RBI)\b""",
            RegexOption.IGNORE_CASE,
        )
        private val APP_RE =
            Regex("""\b(AnyDesk|TeamViewer|Quick ?Support|RustDesk|AirDroid)\b""", RegexOption.IGNORE_CASE)

        private val SHORTENERS = setOf(
            "bit.ly", "tinyurl.com", "t.co", "goo.gl", "cutt.ly", "rb.gy", "is.gd",
            "shorturl.at", "tiny.cc", "rebrand.ly", "ow.ly", "tr.ee", "surl.li",
        )
        private val TRUSTED_DOMAINS = setOf(
            "sbi.co.in", "onlinesbi.sbi", "yonobusiness.sbi", "hdfcbank.com", "icicibank.com",
            "axisbank.com", "kotak.com", "pnbindia.in", "bankofbaroda.in", "canarabank.com",
            "unionbankofindia.co.in", "paytm.com", "phonepe.com", "pay.google.com",
            "npci.org.in", "rbi.org.in", "cybercrime.gov.in", "amazon.in", "flipkart.com",
            "irctc.co.in", "indiapost.gov.in", "bluedart.com", "delhivery.com",
        )
        private val EMAIL_TLDS = setOf("com", "in", "org", "net", "co")
    }
}
