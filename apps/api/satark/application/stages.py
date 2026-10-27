"""Concrete pipeline stages. Each depends only on ports, never on models."""

from __future__ import annotations

import re

from satark.domain import Entity, EntityKind, Signal

from .pipeline import PipelineContext, Stage
from .ports import (
    EntityExtractorPort,
    IntentClassifierPort,
    LanguageDetectorPort,
    PhishingClassifierPort,
    ScamSimilarityPort,
    SpamClassifierPort,
    TranslatorPort,
)

DEVANAGARI = re.compile(r"[\u0900-\u097F]")


# ---------------------------------------------------------------- 01 language
class NormalizeLanguageStage(Stage):
    name = "language"
    label = "Normalize & language"
    weight = 0.0  # informational, does not move the score

    def __init__(self, detector: LanguageDetectorPort, translator: TranslatorPort):
        self._detector = detector
        self._translator = translator

    def run(self, ctx: PipelineContext) -> Signal:
        raw = ctx.original_text
        text = re.sub(r"[ \t]+", " ", raw).strip()
        devanagari_ratio = len(DEVANAGARI.findall(text)) / max(1, len(text))

        lang, conf = self._detector.detect(text)
        # Script is a stronger cue than any classifier for Hindi vs. Hinglish.
        if devanagari_ratio > 0.25:
            lang, conf = "hi", max(conf, 0.95)

        translated = None
        if lang == "hi" and devanagari_ratio > 0.25:
            translated = self._translator.translate(text)
            ctx.translated_text = translated
            ctx.text = translated
        else:
            ctx.text = text

        ctx.language = lang if conf >= 0.5 or devanagari_ratio > 0.25 else "en"
        ctx.language_confidence = conf

        if translated:
            evidence = f"Detected Hindi ({conf:.0%}). Translated to English for the classifiers."
        elif lang != "en" and conf >= 0.5:
            evidence = f"Detected {lang} ({conf:.0%}). Passing text through unchanged."
        elif conf < 0.5:
            evidence = "Mixed-language or short text; treating as English/Hinglish."
        else:
            evidence = f"Detected English ({conf:.0%})."

        return Signal(
            name=self.name,
            label=self.label,
            score=0.0,
            weight=self.weight,
            evidence=evidence,
            details={
                "language": ctx.language,
                "confidence": round(conf, 3),
                "translated": translated,
            },
        )


# ---------------------------------------------------------------- 02 entities
class EntityExtractionStage(Stage):
    name = "entities"
    label = "Entity extraction"
    weight = 0.15

    def __init__(self, extractor: EntityExtractorPort):
        self._extractor = extractor

    def run(self, ctx: PipelineContext) -> Signal:
        # Extract from both the original and working text, plus structured fields.
        source = "\n".join(
            p for p in [ctx.original_text, ctx.text or "", ctx.message.upi_id or "", ctx.message.url or ""] if p
        )
        entities = self._extractor.extract(source)
        ctx.entities = entities

        score = 0.0
        notes: list[str] = []
        for e in entities:
            if e.kind == EntityKind.UPI_VPA:
                if e.valid is False and e.note and "lookalike" in e.note:
                    score = max(score, 0.95)
                    notes.append(f"UPI ID {e.value} uses a lookalike handle ({e.note}).")
                elif e.valid is False:
                    score = max(score, 0.6)
                    notes.append(f"UPI ID {e.value} ends in a handle no bank or PSP uses.")
            elif e.kind == EntityKind.URL:
                if e.note and "shortened" in e.note:
                    score = max(score, 0.55)
                    notes.append(f"Shortened link {e.value} hides its real destination.")
                elif e.note and "apk" in e.note:
                    score = max(score, 0.9)
                    notes.append(f"Link {e.value} points to an APK file.")
                elif e.valid is False:
                    score = max(score, 0.5)
                    notes.append(f"Link {e.value} is not on a known bank or PSP domain.")
            elif e.kind == EntityKind.CREDENTIAL:
                # A mention alone is weak; the rules stage catches the *ask*.
                score = max(score, 0.3)
            elif e.kind == EntityKind.APP:
                score = max(score, 0.8)
                notes.append(f"Mentions remote-access app {e.value}.")

        if not entities:
            evidence = "No UPI IDs, links, phone numbers or amounts found."
        elif not notes:
            kinds = sorted({e.kind.value.replace("_", " ") for e in entities})
            evidence = f"Found {len(entities)} entities ({', '.join(kinds)}); nothing unusual about them."
        else:
            evidence = " ".join(notes[:2])

        return Signal(
            name=self.name,
            label=self.label,
            score=score,
            weight=self.weight,
            evidence=evidence,
            details={"count": len(entities)},
        )


# ---------------------------------------------------------------- 03 spam
class SpamStage(Stage):
    name = "spam"
    label = "Spam classifier"
    weight = 0.10

    def __init__(self, classifier: SpamClassifierPort):
        self._clf = classifier

    def run(self, ctx: PipelineContext) -> Signal:
        p = self._clf.spam_probability(ctx.text)
        if p >= 0.7:
            evidence = f"SMS spam model rates this {p:.0%} spam."
        elif p >= 0.4:
            evidence = f"SMS spam model is unsure ({p:.0%} spam)."
        else:
            evidence = f"SMS spam model rates this {p:.0%} spam — reads like a normal message."
        return Signal(self.name, self.label, p, self.weight, evidence, details={"spam_probability": round(p, 4)})


# ---------------------------------------------------------------- 04 phishing
class PhishingStage(Stage):
    name = "phishing"
    label = "Phishing classifier"
    weight = 0.20

    def __init__(self, classifier: PhishingClassifierPort):
        self._clf = classifier

    def run(self, ctx: PipelineContext) -> Signal:
        p = self._clf.phishing_probability(ctx.message.combined_text() if ctx.text == ctx.original_text else ctx.text)
        # The phishing models are trained on link- and credential-bearing text.
        # Without either, their opinion is weaker, so the stage halves its own weight.
        has_hook = any(e.kind in (EntityKind.URL, EntityKind.CREDENTIAL) for e in ctx.entities)
        weight = self.weight if has_hook else self.weight / 2
        if p >= 0.7:
            evidence = f"Phishing model rates this {p:.0%} phishing."
        elif p >= 0.4:
            evidence = f"Phishing model is unsure ({p:.0%})."
        else:
            evidence = f"Phishing model rates this {p:.0%} phishing — no credential-harvesting pattern."
        return Signal(self.name, self.label, p, weight, evidence, details={"phishing_probability": round(p, 4), "has_link_or_credential": has_hook})


# ---------------------------------------------------------------- 05 intent
INTENT_LABELS: dict[str, float] = {
    # label -> how risky that intent is (0 = benign, 1 = textbook scam)
    "request for OTP or PIN": 1.0,
    "account blocked threat": 0.95,
    "lottery or prize": 0.95,
    "KYC or account verification": 0.9,
    "electricity bill disconnection": 0.9,
    "cashback or refund": 0.85,
    "delivery or customs fee": 0.85,
    "job offer": 0.7,
    "request to send money": 0.5,
    "legitimate bank notification": 0.1,
    "normal personal message": 0.0,
}


class IntentStage(Stage):
    name = "intent"
    label = "Scam intent (zero-shot)"
    weight = 0.20

    def __init__(self, classifier: IntentClassifierPort):
        self._clf = classifier

    def run(self, ctx: PipelineContext) -> Signal:
        ranked = self._clf.classify(ctx.text, list(INTENT_LABELS))
        score = sum(p * INTENT_LABELS.get(label, 0.5) for label, p in ranked)
        top_label, top_p = ranked[0]
        if INTENT_LABELS[top_label] >= 0.6:
            evidence = f"Reads as “{top_label}” ({top_p:.0%}), a common scam pretext."
        else:
            evidence = f"Reads as “{top_label}” ({top_p:.0%})."
        return Signal(
            self.name,
            self.label,
            score,
            self.weight,
            evidence,
            details={"labels": [{"label": l, "score": round(p, 4)} for l, p in ranked[:5]]},
        )


# ---------------------------------------------------------------- 06 similarity
class SimilarityStage(Stage):
    name = "similarity"
    label = "Known-scam similarity"
    weight = 0.20

    def __init__(self, index: ScamSimilarityPort):
        self._index = index

    def run(self, ctx: PipelineContext) -> Signal:
        matches = self._index.nearest(ctx.text, k=5)
        scam_sim = max((m.similarity for m in matches if m.is_scam), default=0.0)
        legit_sim = max((m.similarity for m in matches if not m.is_scam), default=0.0)
        # Contrast between the nearest scam and nearest legitimate template,
        # damped when neither is a close match.
        contrast = (scam_sim - legit_sim) * 2.5 + 0.5
        strength = min(1.0, max(scam_sim, legit_sim) / 0.6)
        score = max(0.0, min(1.0, contrast)) * strength + (1 - strength) * 0.25
        nearest = matches[0]
        if nearest.is_scam:
            evidence = f"{nearest.similarity:.0%} similar to a known “{nearest.category}” scam template."
        else:
            evidence = f"Closest match is a legitimate “{nearest.category}” message ({nearest.similarity:.0%})."
        return Signal(
            self.name,
            self.label,
            score,
            self.weight,
            evidence,
            details={
                "scam_similarity": round(scam_sim, 4),
                "legit_similarity": round(legit_sim, 4),
                "nearest": [
                    {"text": m.text, "category": m.category, "is_scam": m.is_scam, "similarity": round(m.similarity, 4)}
                    for m in matches[:3]
                ],
            },
        )


# ---------------------------------------------------------------- 07 rules
URGENCY = re.compile(
    r"\b(immediately|urgent(ly)?|today|within \d+ (hours?|hrs?|minutes?|mins?)|last (chance|warning)|"
    r"right now|asap|expire[sd]?|will be (blocked|suspended|deactivated|disconnected|closed)|"
    r"final notice|act now|turant|abhi|jaldi)\b",
    re.I,
)
COLLECT_TRICK = re.compile(
    r"(collect request|approve (the |this )?(request|payment)|accept (the |this )?(request|payment) to receive|"
    r"enter (your )?(upi )?pin to receive|pay request|request money sent|approve .{0,20}to (get|receive))",
    re.I,
)
CREDENTIAL_ASK = re.compile(
    r"(share|send|tell|enter|provide|verify|confirm|type)\b.{0,40}\b(otp|pin|cvv|password|mpin|upi pin|card number|atm pin)|"
    r"\b(otp|pin|cvv|mpin)\b.{0,30}\b(share|send|reply|batao|bhejo)",
    re.I,
)
REMOTE_APP = re.compile(r"\b(anydesk|teamviewer|quick ?support|rustdesk|airdroid|screen ?share)\b", re.I)
APK = re.compile(r"\.apk\b|install (the |this )?(app|application)|download (the |this )?(app|application)", re.I)
SHORTENER = re.compile(r"\b(bit\.ly|tinyurl\.com|t\.co|goo\.gl|cutt\.ly|rb\.gy|is\.gd|shorturl\.at|tiny\.cc|rebrand\.ly|ow\.ly)\b", re.I)
PRIZE = re.compile(r"\b(won|winner|lottery|lucky draw|prize|jackpot|congratulations|reward)\b", re.I)
THREAT = re.compile(r"\b(police|arrest|legal action|fir|court|penalty|fine|customs|seized|accident|hospital|emergency)\b", re.I)
HINDI_URGENCY = re.compile(r"तुरंत|अभी|जल्दी|आज रात|आज ही|काट दिया जाएगा|ब्लॉक")
HINDI_ELECTRICITY = re.compile(r"बिजली|बिजली कनेक्शन|बिजली बिल|काट दिया जाएगा")
ELECTRICITY = re.compile(
    r"\b(electricity|power|bijli)\b.{0,80}\b(disconnect\w*|cut( off)?|terminat\w*|suspend\w*)|"
    r"\b(disconnect\w*|cut( off)?|terminat\w*)\b.{0,80}\b(electricity|power|bijli|connection)\b",
    re.I | re.S,
)
BANK_WORDS = {
    "sbi": "sbi",
    "state bank": "sbi",
    "yono": "sbi",
    "hdfc": "hdfc",
    "icici": "icici",
    "axis": "axis",
    "kotak": "kotak",
    "pnb": "pnb",
    "punjab national": "pnb",
    "bank of baroda": "bob",
    "bob": "bob",
    "canara": "canara",
    "union bank": "union",
    "paytm": "paytm",
    "phonepe": "phonepe",
    "google pay": "gpay",
    "gpay": "gpay",
}
HANDLE_BANK = {
    "oksbi": "sbi", "sbi": "sbi",
    "okhdfcbank": "hdfc", "hdfcbank": "hdfc", "wahdfcbank": "hdfc",
    "okicici": "icici", "icici": "icici", "ibl": "icici", "waicici": "icici",
    "okaxis": "axis", "axisbank": "axis", "axl": "axis", "jupiteraxis": "axis", "waaxis": "axis",
    "kotak": "kotak", "kmbl": "kotak",
    "pnb": "pnb", "barodampay": "bob", "cnrb": "canara", "uboi": "union",
    "paytm": "paytm", "ptyes": "paytm", "ptsbi": "paytm", "ptaxis": "paytm", "pthdfc": "paytm",
    "ybl": "phonepe", "ibl": "phonepe", "axl": "phonepe",
}


class RuleSignalsStage(Stage):
    name = "rules"
    label = "Rule signals"
    weight = 0.15

    def run(self, ctx: PipelineContext) -> Signal:
        text = f"{ctx.original_text}\n{ctx.text}"
        fired: list[tuple[str, float]] = []

        if CREDENTIAL_ASK.search(text):
            fired.append(("Asks you to share or enter an OTP, PIN or CVV.", 1.0))
        if COLLECT_TRICK.search(text):
            fired.append(("Asks you to approve a request or enter a PIN to receive money — receiving money never needs that.", 1.0))
        if REMOTE_APP.search(text):
            fired.append(("Asks you to install a remote-access app such as AnyDesk or TeamViewer.", 0.95))
        if APK.search(text):
            fired.append(("Pushes you to install an app or APK from a link.", 0.8))
        if SHORTENER.search(text):
            fired.append(("Uses a shortened link that hides the real website.", 0.6))
        if URGENCY.search(text) or HINDI_URGENCY.search(text):
            fired.append(("Creates time pressure (“immediately”, “today”, “will be blocked”).", 0.6))
        if HINDI_ELECTRICITY.search(text) or ELECTRICITY.search(text):
            fired.append(("Threatens electricity disconnection unless you call or pay immediately.", 0.7))
        if PRIZE.search(text):
            fired.append(("Promises a prize, reward or lottery win.", 0.7))
        if THREAT.search(text):
            fired.append(("Uses a threat or emergency (police, customs, accident) to rush you.", 0.6))

        # Claimed bank vs. VPA handle mismatch
        claimed = {bank for word, bank in BANK_WORDS.items() if re.search(rf"\b{re.escape(word)}\b", text, re.I)}
        vpas = [e for e in ctx.entities if e.kind == EntityKind.UPI_VPA]
        for v in vpas:
            handle = v.value.rsplit("@", 1)[-1].lower()
            handle_bank = HANDLE_BANK.get(handle)
            if claimed and handle_bank and handle_bank not in claimed and not ({"paytm", "phonepe", "gpay"} & claimed):
                fired.append((f"Claims to be from {', '.join(sorted(claimed)).upper()} but the UPI ID {v.value} belongs to another bank.", 0.8))

        if not fired:
            score, evidence = 0.0, "No urgency, credential request, remote-app or prize pattern."
        else:
            top = max(w for _, w in fired)
            score = min(1.0, top + 0.1 * (len(fired) - 1))
            evidence = fired[0][0]

        return Signal(
            self.name,
            self.label,
            score,
            self.weight,
            evidence,
            details={"rules": [r for r, _ in fired]},
        )


__all__ = [
    "NormalizeLanguageStage",
    "EntityExtractionStage",
    "SpamStage",
    "PhishingStage",
    "IntentStage",
    "SimilarityStage",
    "RuleSignalsStage",
    "INTENT_LABELS",
]
