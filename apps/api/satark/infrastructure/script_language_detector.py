"""Unicode-script language detector used when the transformer model is not on disk.

Indian payment messages are overwhelmingly English, Hindi (Devanagari) or
romanised Hinglish. Script ranges settle the first two; a small stop-word
vote separates English from Hinglish.
"""

from __future__ import annotations

import re

from satark.application.ports import LanguageDetectorPort

SCRIPTS: list[tuple[str, re.Pattern[str]]] = [
    ("hi", re.compile(r"[\u0900-\u097F]")),  # Devanagari (hi / mr)
    ("bn", re.compile(r"[\u0980-\u09FF]")),
    ("pa", re.compile(r"[\u0A00-\u0A7F]")),
    ("gu", re.compile(r"[\u0A80-\u0AFF]")),
    ("ta", re.compile(r"[\u0B80-\u0BFF]")),
    ("te", re.compile(r"[\u0C00-\u0C7F]")),
    ("kn", re.compile(r"[\u0C80-\u0CFF]")),
    ("ml", re.compile(r"[\u0D00-\u0D7F]")),
    ("ur", re.compile(r"[\u0600-\u06FF]")),
]

HINGLISH = {
    "hai", "hain", "nahi", "nahin", "kya", "aap", "aapka", "aapke", "apna", "karo", "karein", "kar", "kare",
    "bhejo", "bhej", "diya", "paise", "paisa", "rupaye", "turant", "abhi", "jaldi", "mera", "meri", "tum",
    "tumhara", "hoga", "hogi", "gaya", "gayi", "lena", "dena", "daal", "dalo", "liye", "ke", "ki", "ka", "se",
    "mein", "par", "aur", "bhi", "toh", "yeh", "woh", "kuch", "bahut", "accha", "theek", "bhai", "didi",
}
ENGLISH = {
    "the", "is", "are", "your", "you", "to", "and", "of", "for", "will", "be", "please", "account", "with",
    "this", "that", "from", "on", "in", "it", "have", "has", "not", "or", "by", "at", "an", "a", "as", "if",
}


class ScriptLanguageDetector(LanguageDetectorPort):
    model_id = "unicode-script heuristic"
    loaded = True

    def warm(self) -> None:  # noqa: D401 — parity with HF adapters
        return None

    def detect(self, text: str) -> tuple[str, float]:
        letters = [c for c in text if c.isalpha()]
        if not letters:
            return "en", 0.5
        for code, pat in SCRIPTS:
            hits = len(pat.findall(text))
            if hits / len(letters) > 0.3:
                return code, min(0.99, 0.6 + hits / len(letters) * 0.4)
        words = re.findall(r"[a-z]+", text.lower())
        if not words:
            return "en", 0.5
        hing = sum(w in HINGLISH for w in words)
        eng = sum(w in ENGLISH for w in words)
        if hing > eng and hing >= 2:
            return "hi-latn", min(0.9, 0.5 + hing / len(words))
        conf = min(0.95, 0.55 + eng / max(1, len(words)))
        return "en", conf
