from satark.application.risk_fusion import RiskFusionService
from satark.domain import Entity, EntityKind, Signal, VerdictLabel


def sig(name, score, weight, evidence="", details=None):
    return Signal(name, name.title(), score, weight, evidence or f"{name} evidence", 1, details or {})


def all_low():
    return [
        sig("language", 0.0, 0.0),
        sig("entities", 0.0, 0.15, "No entities found."),
        sig("spam", 0.03, 0.10, "Spam model rates this 3% spam."),
        sig("phishing", 0.05, 0.20, "Phishing model rates this 5%."),
        sig("intent", 0.1, 0.20, "Reads as normal personal message."),
        sig("similarity", 0.1, 0.20, "Closest match is a legitimate friend payment."),
        sig("rules", 0.0, 0.15, "No rules fired.", {"rules": []}),
    ]


def all_high():
    return [
        sig("language", 0.0, 0.0),
        sig("entities", 0.6, 0.15),
        sig("spam", 0.95, 0.10),
        sig("phishing", 0.9, 0.20),
        sig("intent", 0.85, 0.20),
        sig("similarity", 0.9, 0.20),
        sig("rules", 0.8, 0.15, "x", {"rules": ["Creates time pressure (“immediately”, “today”, “will be blocked”)."]}),
    ]


def test_safe_message_scores_low():
    v = RiskFusionService().fuse(all_low(), [])
    assert v.label == VerdictLabel.SAFE
    assert v.score < 30
    assert v.reasons and v.advice
    assert 0 <= v.confidence <= 1


def test_fraud_message_scores_high():
    v = RiskFusionService().fuse(all_high(), [])
    assert v.label == VerdictLabel.FRAUD
    assert v.score > 65
    assert any("1930" in a for a in v.advice)


def test_language_weight_is_ignored():
    signals = all_low()
    signals[0] = sig("language", 1.0, 0.0)
    assert RiskFusionService().fuse(signals, []).score == RiskFusionService().fuse(all_low(), []).score


def test_weighted_mean_is_deterministic():
    signals = [sig("a", 0.5, 0.5), sig("b", 1.0, 0.5), sig("rules", 0.0, 0.0, "", {"rules": []})]
    assert RiskFusionService().fuse(signals, []).score == 75


def test_middle_scores_are_suspicious():
    signals = [sig("a", 0.45, 1.0), sig("rules", 0.0, 0.0, "", {"rules": []})]
    v = RiskFusionService().fuse(signals, [])
    assert v.label == VerdictLabel.SUSPICIOUS


def test_lookalike_vpa_floors_score_to_fraud():
    entities = [Entity(EntityKind.UPI_VPA, "x@okaxls", False, "lookalike of @okaxis")]
    v = RiskFusionService().fuse(all_low(), entities)
    assert v.score >= 70
    assert v.label == VerdictLabel.FRAUD
    assert any("okaxls" in r for r in v.reasons)
    assert any("Do not send money" in a for a in v.advice)


def test_otp_request_floors_score():
    signals = all_low()
    signals[-1] = sig("rules", 1.0, 0.15, "", {"rules": ["Asks you to share or enter an OTP, PIN or CVV."]})
    v = RiskFusionService().fuse(signals, [])
    assert v.score >= 70
    assert any("OTP" in a for a in v.advice)


def test_collect_request_advice():
    signals = all_low()
    signals[-1] = sig("rules", 1.0, 0.15, "", {"rules": ["Asks you to approve a request or enter a PIN to receive money — receiving money never needs that."]})
    v = RiskFusionService().fuse(signals, [])
    assert v.label == VerdictLabel.FRAUD
    assert any("collect" in a.lower() for a in v.advice)
