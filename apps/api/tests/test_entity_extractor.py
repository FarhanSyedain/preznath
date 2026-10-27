from satark.domain import EntityKind
from satark.infrastructure.entity_extractor import RegexEntityExtractor
from satark.infrastructure.upi_registry import UpiHandleRegistry


def extract(text: str):
    return RegexEntityExtractor(UpiHandleRegistry()).extract(text)


def by_kind(entities, kind):
    return [e for e in entities if e.kind == kind]


def test_extracts_known_vpa():
    (vpa,) = by_kind(extract("Pay to ravi.kumar@okaxis"), EntityKind.UPI_VPA)
    assert vpa.value == "ravi.kumar@okaxis"
    assert vpa.valid is True
    assert "Axis" in (vpa.note or "")


def test_flags_unknown_and_lookalike_vpa():
    ents = by_kind(extract("send to a@zzzqqq and b@okaxls"), EntityKind.UPI_VPA)
    notes = {e.value: e for e in ents}
    assert notes["a@zzzqqq"].valid is False and notes["a@zzzqqq"].note == "unknown handle"
    assert notes["b@okaxls"].valid is False and "lookalike of @okaxis" in notes["b@okaxls"].note


def test_ignores_email_addresses():
    ents = extract("mail me at farhan@gmail.com")
    assert by_kind(ents, EntityKind.UPI_VPA) == []
    assert by_kind(ents, EntityKind.URL) == []


def test_urls_shortened_trusted_unknown_apk():
    ents = by_kind(
        extract("http://bit.ly/x https://www.sbi.co.in/kyc http://sbi-kyc-update.xyz/login https://evil.site/yono.apk"),
        EntityKind.URL,
    )
    notes = {e.value: e.note for e in ents}
    assert notes["http://bit.ly/x"] == "shortened link"
    assert notes["https://www.sbi.co.in/kyc"] == "known domain"
    assert notes["http://sbi-kyc-update.xyz/login"] == "unknown domain"
    assert notes["https://evil.site/yono.apk"] == "apk download"


def test_phone_amount_credential_bank_app():
    ents = extract("Call +91 98765 43210 or 09876543211. Pay Rs 1,500 or ₹200. Share OTP. Install AnyDesk. SBI YONO")
    assert {e.value for e in by_kind(ents, EntityKind.PHONE)} == {"9876543210", "9876543211"}
    assert {e.value for e in by_kind(ents, EntityKind.AMOUNT)} == {"₹1,500", "₹200"}
    assert {e.value for e in by_kind(ents, EntityKind.CREDENTIAL)} == {"OTP"}
    assert {e.value for e in by_kind(ents, EntityKind.BANK)} == {"SBI", "YONO"}
    assert {e.value for e in by_kind(ents, EntityKind.APP)} == {"AnyDesk"}


def test_no_entities_in_plain_text():
    assert extract("Meeting moved to 4 pm tomorrow.") == []
