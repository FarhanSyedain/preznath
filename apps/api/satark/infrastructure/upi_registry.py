"""Registry of real NPCI PSP / bank UPI handles, with lookalike detection."""

from __future__ import annotations

from satark.domain.ports import UpiHandleRegistryPort

# handle -> bank / PSP. Sources: NPCI live-member list and the handles the
# major apps issue (Google Pay ok*, PhonePe ybl/ibl/axl, Paytm pt*, WhatsApp wa*).
KNOWN_HANDLES: dict[str, str] = {
    # Google Pay
    "okaxis": "Axis Bank (Google Pay)", "oksbi": "SBI (Google Pay)",
    "okhdfcbank": "HDFC Bank (Google Pay)", "okicici": "ICICI Bank (Google Pay)",
    # PhonePe
    "ybl": "Yes Bank (PhonePe)", "ibl": "ICICI Bank (PhonePe)", "axl": "Axis Bank (PhonePe)",
    # Paytm
    "paytm": "Paytm Payments Bank", "ptyes": "Yes Bank (Paytm)", "ptsbi": "SBI (Paytm)",
    "ptaxis": "Axis Bank (Paytm)", "pthdfc": "HDFC Bank (Paytm)",
    # Amazon Pay
    "apl": "Axis Bank (Amazon Pay)", "yapl": "Yes Bank (Amazon Pay)", "rapl": "RBL Bank (Amazon Pay)",
    # WhatsApp Pay
    "waaxis": "Axis Bank (WhatsApp)", "wahdfcbank": "HDFC Bank (WhatsApp)",
    "waicici": "ICICI Bank (WhatsApp)", "wasbi": "SBI (WhatsApp)",
    # BHIM / banks
    "upi": "BHIM", "sbi": "State Bank of India", "hdfcbank": "HDFC Bank", "icici": "ICICI Bank",
    "axisbank": "Axis Bank", "kotak": "Kotak Mahindra Bank", "kmbl": "Kotak Mahindra Bank",
    "indus": "IndusInd Bank", "idfcbank": "IDFC First Bank", "idfcfirst": "IDFC First Bank",
    "fbl": "Federal Bank", "federal": "Federal Bank", "pnb": "Punjab National Bank",
    "barodampay": "Bank of Baroda", "cnrb": "Canara Bank", "uboi": "Union Bank of India",
    "boi": "Bank of India", "cbin": "Central Bank of India", "indianbank": "Indian Bank",
    "iob": "Indian Overseas Bank", "mahb": "Bank of Maharashtra", "psb": "Punjab & Sind Bank",
    "ucobank": "UCO Bank", "yesbank": "Yes Bank", "rbl": "RBL Bank", "citi": "Citibank",
    "hsbc": "HSBC", "sc": "Standard Chartered", "dbs": "DBS Bank", "idbi": "IDBI Bank",
    "jupiteraxis": "Axis Bank (Jupiter)", "fifederal": "Federal Bank (Fi)", "niyoicici": "ICICI Bank (Niyo)",
    "freecharge": "Freecharge", "airtel": "Airtel Payments Bank", "jio": "Jio Payments Bank",
    "postbank": "India Post Payments Bank", "ippb": "India Post Payments Bank",
    "aubank": "AU Small Finance Bank", "equitas": "Equitas SFB", "ujjivan": "Ujjivan SFB",
    "slice": "Slice (NESFB)", "timecosmos": "Cosmos Bank", "tapicici": "ICICI Bank (Tap)",
    "superyes": "Yes Bank (super.money)", "naviaxis": "Axis Bank (Navi)", "goaxis": "Axis Bank",
    "icicibank": "ICICI Bank", "axb": "Axis Bank", "hdfc": "HDFC Bank",
}


def levenshtein(a: str, b: str) -> int:
    if a == b:
        return 0
    if not a:
        return len(b)
    if not b:
        return len(a)
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ca != cb)))
        prev = cur
    return prev[-1]


class UpiHandleRegistry(UpiHandleRegistryPort):
    def __init__(self, handles: dict[str, str] | None = None):
        self._handles = {k.lower(): v for k, v in (handles or KNOWN_HANDLES).items()}

    def is_known(self, handle: str) -> bool:
        return handle.lower() in self._handles

    def bank_for(self, handle: str) -> str | None:
        return self._handles.get(handle.lower())

    def nearest(self, handle: str) -> tuple[str, int] | None:
        h = handle.lower()
        if not h:
            return None
        best = min(self._handles, key=lambda k: levenshtein(h, k))
        return best, levenshtein(h, best)

    def is_lookalike(self, handle: str) -> tuple[str, int] | None:
        """A handle that is not known but is 1–2 edits from a real one."""
        if self.is_known(handle):
            return None
        near = self.nearest(handle)
        if near is None:
            return None
        best, dist = near
        # Short handles (ybl, sbi) need distance 1; longer ones allow 2.
        limit = 1 if len(best) <= 4 else 2
        return (best, dist) if dist <= limit else None
