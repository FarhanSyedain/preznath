package com.yimberzol.preznath.data

/**
 * NPCI PSP / bank UPI handle registry with Levenshtein lookalike detection.
 */
class HandleRegistry(handles: Map<String, String> = KNOWN_HANDLES) {
    private val handles = handles.mapKeys { it.key.lowercase() }

    fun isKnown(handle: String): Boolean = handle.lowercase() in handles

    fun bankFor(handle: String): String? = handles[handle.lowercase()]

    fun nearest(handle: String): Pair<String, Int>? {
        val h = handle.lowercase()
        if (h.isEmpty() || handles.isEmpty()) return null
        val best = handles.keys.minBy { levenshtein(h, it) }
        return best to levenshtein(h, best)
    }

    /** Unknown handle that is 1–2 edits from a real one. */
    fun isLookalike(handle: String): Pair<String, Int>? {
        if (isKnown(handle)) return null
        val near = nearest(handle) ?: return null
        val limit = if (near.first.length <= 4) 1 else 2
        return if (near.second <= limit) near else null
    }

    companion object {
        val KNOWN_HANDLES: Map<String, String> = mapOf(
            "okaxis" to "Axis Bank (Google Pay)",
            "oksbi" to "SBI (Google Pay)",
            "okhdfcbank" to "HDFC Bank (Google Pay)",
            "okicici" to "ICICI Bank (Google Pay)",
            "ybl" to "Yes Bank (PhonePe)",
            "ibl" to "ICICI Bank (PhonePe)",
            "axl" to "Axis Bank (PhonePe)",
            "paytm" to "Paytm Payments Bank",
            "ptyes" to "Yes Bank (Paytm)",
            "ptsbi" to "SBI (Paytm)",
            "ptaxis" to "Axis Bank (Paytm)",
            "pthdfc" to "HDFC Bank (Paytm)",
            "apl" to "Axis Bank (Amazon Pay)",
            "yapl" to "Yes Bank (Amazon Pay)",
            "rapl" to "RBL Bank (Amazon Pay)",
            "waaxis" to "Axis Bank (WhatsApp)",
            "wahdfcbank" to "HDFC Bank (WhatsApp)",
            "waicici" to "ICICI Bank (WhatsApp)",
            "wasbi" to "SBI (WhatsApp)",
            "upi" to "BHIM",
            "sbi" to "State Bank of India",
            "hdfcbank" to "HDFC Bank",
            "icici" to "ICICI Bank",
            "axisbank" to "Axis Bank",
            "kotak" to "Kotak Mahindra Bank",
            "kmbl" to "Kotak Mahindra Bank",
            "indus" to "IndusInd Bank",
            "idfcbank" to "IDFC First Bank",
            "idfcfirst" to "IDFC First Bank",
            "fbl" to "Federal Bank",
            "federal" to "Federal Bank",
            "pnb" to "Punjab National Bank",
            "barodampay" to "Bank of Baroda",
            "cnrb" to "Canara Bank",
            "uboi" to "Union Bank of India",
            "boi" to "Bank of India",
            "cbin" to "Central Bank of India",
            "indianbank" to "Indian Bank",
            "iob" to "Indian Overseas Bank",
            "mahb" to "Bank of Maharashtra",
            "psb" to "Punjab & Sind Bank",
            "ucobank" to "UCO Bank",
            "yesbank" to "Yes Bank",
            "rbl" to "RBL Bank",
            "citi" to "Citibank",
            "hsbc" to "HSBC",
            "sc" to "Standard Chartered",
            "dbs" to "DBS Bank",
            "idbi" to "IDBI Bank",
            "jupiteraxis" to "Axis Bank (Jupiter)",
            "fifederal" to "Federal Bank (Fi)",
            "niyoicici" to "ICICI Bank (Niyo)",
            "freecharge" to "Freecharge",
            "airtel" to "Airtel Payments Bank",
            "jio" to "Jio Payments Bank",
            "postbank" to "India Post Payments Bank",
            "ippb" to "India Post Payments Bank",
            "aubank" to "AU Small Finance Bank",
            "equitas" to "Equitas SFB",
            "ujjivan" to "Ujjivan SFB",
            "slice" to "Slice (NESFB)",
            "timecosmos" to "Cosmos Bank",
            "tapicici" to "ICICI Bank (Tap)",
            "superyes" to "Yes Bank (super.money)",
            "naviaxis" to "Axis Bank (Navi)",
            "goaxis" to "Axis Bank",
            "icicibank" to "ICICI Bank",
            "axb" to "Axis Bank",
            "hdfc" to "HDFC Bank",
        )

        fun levenshtein(a: String, b: String): Int {
            if (a == b) return 0
            if (a.isEmpty()) return b.length
            if (b.isEmpty()) return a.length
            var prev = IntArray(b.length + 1) { it }
            for (i in 1..a.length) {
                val cur = IntArray(b.length + 1)
                cur[0] = i
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                }
                prev = cur
            }
            return prev[b.length]
        }
    }
}
