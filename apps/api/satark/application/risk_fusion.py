"""Deterministic risk fusion. Pure Python; no I/O, no models.

Score = weighted mean of stage scores (weights below), scaled to 0..100.

    entities    0.15   unknown / lookalike UPI handle, shortened or APK links
    spam        0.10   mrm8488/bert-tiny-finetuned-sms-spam-detection
    phishing    0.20   ealvaradob/bert-finetuned-phishing
    intent      0.20   zero-shot scam-pretext classification
    similarity  0.20   nearest known scam vs. legitimate template
    rules       0.15   hand-written UPI-specific red flags
    language    0.00   informational only

(The phishing stage halves its own weight when the message has no link and
no OTP/PIN mention, because the model was trained on link-bearing text.)

Adjustments, applied in this order:

1. Legitimate-template dampening. The similarity index holds real bank
   alerts, OTP messages and friendly payment notes. If the nearest template
   is legitimate with cosine >= 0.75 and nothing hard fired, the raw score is
   multiplied by 0.4..0.7 (stronger match, stronger dampening). This is what
   stops a genuine "Rs 500 debited" SMS from being flagged just because
   generic spam/phishing models dislike formal bank language.
2. Agreement bonus. If three or more weighted signals score >= 0.7, add 10.
   Independent detectors agreeing is stronger evidence than any one of them.
3. Hard floors. A few patterns are so specific to UPI fraud that no polite
   wording should rescue them:

    lookalike UPI handle                     -> at least 70
    asks for OTP / PIN / CVV                 -> at least 70
    "approve request / enter PIN to receive" -> at least 70
    remote-access app (AnyDesk etc.)         -> at least 65

Thresholds:  < 30 SAFE   |   30..65 SUSPICIOUS   |   > 65 FRAUD
"""

from __future__ import annotations

from satark.domain import Entity, EntityKind, Signal, Verdict, VerdictLabel

SAFE_BELOW = 30
FRAUD_ABOVE = 65
LEGIT_DAMPEN_FROM = 0.75
AGREEMENT_MIN_SIGNALS = 3
AGREEMENT_THRESHOLD = 0.7
AGREEMENT_BONUS = 10

HARD_FLOORS: list[tuple[str, int]] = [
    ("lookalike", 70),
    ("OTP, PIN or CVV", 70),
    ("to receive money", 70),
    ("remote-access", 65),
]

HELPLINE_ADVICE = [
    "If you already paid, call the national cybercrime helpline 1930 right away and file a report at cybercrime.gov.in. Reporting within an hour gives the best chance of freezing the money.",
]


class RiskFusionService:
    def fuse(self, signals: list[Signal], entities: list[Entity]) -> Verdict:
        weighted = [(s, s.weight) for s in signals if s.weight > 0]
        total_w = sum(w for _, w in weighted) or 1.0
        raw = sum(s.clamped() * w for s, w in weighted) / total_w

        floor = self._hard_floor(signals, entities)
        dampen = self._legit_dampening(signals) if floor == 0 else 1.0
        raw *= dampen
        score = round(raw * 100)

        if dampen == 1.0:
            strong = sum(1 for s, _ in weighted if s.clamped() >= AGREEMENT_THRESHOLD)
            if strong >= AGREEMENT_MIN_SIGNALS:
                score += AGREEMENT_BONUS

        score = max(score, floor)
        score = max(0, min(100, score))

        label = self._label(score)
        confidence = self._confidence(score, signals)
        reasons = self._reasons(signals, entities, label)
        advice = self._advice(signals, entities, label)
        return Verdict(label=label, score=score, confidence=confidence, reasons=reasons, advice=advice)

    # -- helpers ------------------------------------------------------------
    @staticmethod
    def _label(score: int) -> VerdictLabel:
        if score < SAFE_BELOW:
            return VerdictLabel.SAFE
        if score > FRAUD_ABOVE:
            return VerdictLabel.FRAUD
        return VerdictLabel.SUSPICIOUS

    @staticmethod
    def _legit_dampening(signals: list[Signal]) -> float:
        """1.0 = no change; down to 0.4 when the text matches a known legitimate template."""
        sim = next((s for s in signals if s.name == "similarity"), None)
        rules = next((s for s in signals if s.name == "rules"), None)
        ents = next((s for s in signals if s.name == "entities"), None)
        if sim is None:
            return 1.0
        legit = float(sim.details.get("legit_similarity", 0.0))
        scam = float(sim.details.get("scam_similarity", 0.0))
        if legit < LEGIT_DAMPEN_FROM or legit <= scam:
            return 1.0
        if (rules and rules.clamped() > 0) or (ents and ents.clamped() >= 0.5):
            return 1.0
        strength = min(1.0, (legit - 0.6) / 0.3)  # 0.75 -> 0.5, 0.9+ -> 1.0
        return 1.0 - 0.6 * strength

    @staticmethod
    def _hard_floor(signals: list[Signal], entities: list[Entity]) -> int:
        floor = 0
        rules = next((s for s in signals if s.name == "rules"), None)
        fired: list[str] = list(rules.details.get("rules", [])) if rules else []
        for e in entities:
            if e.kind == EntityKind.UPI_VPA and e.note and "lookalike" in e.note:
                fired.append("lookalike")
        for needle, value in HARD_FLOORS:
            if any(needle in r for r in fired):
                floor = max(floor, value)
        return floor

    @staticmethod
    def _confidence(score: int, signals: list[Signal]) -> float:
        # Distance from the nearest decision boundary, plus agreement between models.
        dist = min(abs(score - SAFE_BELOW), abs(score - FRAUD_ABOVE))
        boundary = min(1.0, dist / 20)
        scored = [s.clamped() for s in signals if s.weight > 0]
        if len(scored) >= 2:
            mean = sum(scored) / len(scored)
            var = sum((x - mean) ** 2 for x in scored) / len(scored)
            agreement = max(0.0, 1.0 - var * 4)
        else:
            agreement = 0.5
        return round(0.4 + 0.35 * boundary + 0.25 * agreement, 2)

    @staticmethod
    def _reasons(signals: list[Signal], entities: list[Entity], label: VerdictLabel) -> list[str]:
        reasons: list[str] = []
        rules = next((s for s in signals if s.name == "rules"), None)
        if rules:
            reasons.extend(rules.details.get("rules", []))
        for e in entities:
            if e.kind == EntityKind.UPI_VPA and e.valid is False:
                reasons.append(
                    f"The UPI ID {e.value} ends in “@{e.value.rsplit('@', 1)[-1]}”, which {e.note or 'no bank or PSP uses'}."
                )
        ranked = sorted((s for s in signals if s.weight > 0 and s.name != "rules"), key=lambda s: s.clamped() * s.weight, reverse=True)
        for s in ranked:
            if label == VerdictLabel.SAFE:
                if s.clamped() < 0.35:
                    reasons.append(s.evidence)
            elif s.clamped() >= 0.5:
                reasons.append(s.evidence)
        # de-duplicate while preserving order
        seen: set[str] = set()
        out = [r for r in reasons if not (r in seen or seen.add(r))]
        return out[:6]

    @staticmethod
    def _advice(signals: list[Signal], entities: list[Entity], label: VerdictLabel) -> list[str]:
        rules = next((s for s in signals if s.name == "rules"), None)
        fired = " ".join(rules.details.get("rules", [])) if rules else ""
        advice: list[str] = []

        if label == VerdictLabel.SAFE:
            advice.append("Nothing here looks like a scam pattern. Still confirm the sender if money is involved.")
            advice.append("Never share an OTP or UPI PIN with anyone, even if the message itself is genuine.")
            return advice

        if "to receive money" in fired:
            advice.append("Do not approve any “collect” request — receiving money never requires approving a request or entering your PIN.")
        if "OTP, PIN or CVV" in fired:
            advice.append("Do not share the OTP, PIN or CVV. No bank, NPCI or payment app will ever ask for them.")
        if "remote-access" in fired:
            advice.append("Do not install AnyDesk, TeamViewer or any screen-sharing app on request. It gives the caller control of your phone.")
        if any(e.kind == EntityKind.URL for e in entities):
            advice.append("Do not open the link. Open your bank's official app or type its website address yourself.")
        if any(e.kind == EntityKind.UPI_VPA and e.valid is False for e in entities):
            advice.append("Do not send money to this UPI ID. Verify the payee name inside your UPI app before paying anyone.")
        if "electricity" in fired:
            advice.append("Electricity boards do not disconnect supply over SMS or WhatsApp. Check your bill in the official discom app or website, not via a number in the message.")
        elif "time pressure" in fired or "blocked" in fired:
            advice.append("Ignore the deadline. Banks do not block accounts over SMS; call the number printed on your card to check.")
        if "prize" in fired:
            advice.append("You cannot win a lottery you did not enter. Any fee asked to release a prize is the scam.")

        if label == VerdictLabel.SUSPICIOUS and not advice:
            advice.append("Treat this with care. Confirm through a channel you already trust before sending money or tapping links.")

        advice.append("Report the sender to your bank and forward the message to 1930 or cybercrime.gov.in so others are warned.")
        advice.extend(HELPLINE_ADVICE)
        return advice[:6]
