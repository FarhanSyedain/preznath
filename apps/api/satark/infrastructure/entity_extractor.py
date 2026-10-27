"""Regex-based entity extraction tuned for Indian payment messages."""

from __future__ import annotations

import re

from satark.application.ports import EntityExtractorPort
from satark.domain import Entity, EntityKind

from .upi_registry import UpiHandleRegistry

VPA_RE = re.compile(r"(?<![\w.])([A-Za-z0-9][\w.\-]{0,60})@([a-zA-Z][a-zA-Z0-9]{1,30})\b")
# Indian mobile numbers (+91 / 0 prefix optional), tolerant of spaces/hyphens.
PHONE_RE = re.compile(r"(?<!\d)(?:\+?91[\s-]?|0)?([6-9]\d{4}[\s-]?\d{5})(?!\d)")
URL_RE = re.compile(
    r"\b((?:https?://|www\.)[^\s<>\"']+|(?:[a-z0-9-]+\.)+(?:com|in|net|org|co|ly|gl|xyz|info|site|online|top|live|app|me|cc|io|link|club|vip|shop)(?:/[^\s<>\"']*)?)",
    re.I,
)
AMOUNT_RE = re.compile(r"(?:₹|Rs\.?|INR)\s?([\d,]+(?:\.\d{1,2})?)|\b([\d,]+(?:\.\d{1,2})?)\s?(?:rupees|rs|inr)\b", re.I)
CREDENTIAL_RE = re.compile(r"\b(OTP|UPI PIN|MPIN|PIN|CVV|password|ATM PIN|card number)\b", re.I)
BANK_RE = re.compile(
    r"\b(SBI|State Bank|YONO|HDFC|ICICI|Axis|Kotak|PNB|Punjab National|Bank of Baroda|BOB|Canara|Union Bank|"
    r"IndusInd|IDFC|Yes Bank|Federal|RBL|Paytm|PhonePe|Google Pay|GPay|BHIM|NPCI|RBI)\b",
    re.I,
)
APP_RE = re.compile(r"\b(AnyDesk|TeamViewer|Quick ?Support|RustDesk|AirDroid)\b", re.I)
SHORTENERS = {"bit.ly", "tinyurl.com", "t.co", "goo.gl", "cutt.ly", "rb.gy", "is.gd", "shorturl.at", "tiny.cc", "rebrand.ly", "ow.ly", "tr.ee", "surl.li"}
TRUSTED_DOMAINS = {
    "sbi.co.in", "onlinesbi.sbi", "yonobusiness.sbi", "hdfcbank.com", "icicibank.com", "axisbank.com",
    "kotak.com", "pnbindia.in", "bankofbaroda.in", "canarabank.com", "unionbankofindia.co.in",
    "paytm.com", "phonepe.com", "pay.google.com", "npci.org.in", "rbi.org.in", "cybercrime.gov.in",
    "amazon.in", "flipkart.com", "irctc.co.in", "indiapost.gov.in", "bluedart.com", "delhivery.com",
}
# Common email domains that look like VPAs but are not.
EMAIL_TLDS = {"com", "in", "org", "net", "co"}


def _domain(url: str) -> str:
    u = re.sub(r"^https?://", "", url, flags=re.I)
    u = re.sub(r"^www\.", "", u, flags=re.I)
    return u.split("/")[0].split("?")[0].lower()


class RegexEntityExtractor(EntityExtractorPort):
    def __init__(self, registry: UpiHandleRegistry):
        self._registry = registry

    def extract(self, text: str) -> list[Entity]:
        out: list[Entity] = []
        seen: set[tuple[EntityKind, str]] = set()

        def add(e: Entity) -> None:
            key = (e.kind, e.value.lower())
            if key not in seen:
                seen.add(key)
                out.append(e)

        for m in VPA_RE.finditer(text):
            user, handle = m.group(1), m.group(2)
            # user@gmail.com style emails are not VPAs
            after = text[m.end(): m.end() + 1]
            if after == "." or handle.lower() in EMAIL_TLDS:
                continue
            vpa = f"{user}@{handle}"
            if self._registry.is_known(handle):
                add(Entity(EntityKind.UPI_VPA, vpa, True, self._registry.bank_for(handle)))
            else:
                look = self._registry.is_lookalike(handle)
                if look:
                    add(Entity(EntityKind.UPI_VPA, vpa, False, f"lookalike of @{look[0]}"))
                else:
                    add(Entity(EntityKind.UPI_VPA, vpa, False, "unknown handle"))

        for m in URL_RE.finditer(text):
            url = m.group(1).rstrip(".,;:)")
            before = text[m.start() - 1] if m.start() > 0 else ""
            if "@" in url or before == "@" or before == ".":
                continue
            dom = _domain(url)
            if dom in SHORTENERS or any(dom.endswith("." + s) for s in SHORTENERS):
                add(Entity(EntityKind.URL, url, False, "shortened link"))
            elif url.lower().endswith(".apk"):
                add(Entity(EntityKind.URL, url, False, "apk download"))
            elif dom in TRUSTED_DOMAINS or any(dom.endswith("." + t) for t in TRUSTED_DOMAINS):
                add(Entity(EntityKind.URL, url, True, "known domain"))
            else:
                add(Entity(EntityKind.URL, url, False, "unknown domain"))

        for m in PHONE_RE.finditer(text):
            digits = re.sub(r"\D", "", m.group(1))
            if len(digits) == 10:
                add(Entity(EntityKind.PHONE, digits))

        for m in AMOUNT_RE.finditer(text):
            val = m.group(1) or m.group(2)
            if val:
                add(Entity(EntityKind.AMOUNT, f"₹{val}"))

        for m in CREDENTIAL_RE.finditer(text):
            add(Entity(EntityKind.CREDENTIAL, m.group(1).upper()))

        for m in BANK_RE.finditer(text):
            add(Entity(EntityKind.BANK, m.group(1)))

        for m in APP_RE.finditer(text):
            add(Entity(EntityKind.APP, m.group(1)))

        return out
