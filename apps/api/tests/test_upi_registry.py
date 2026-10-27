import pytest

from satark.infrastructure.upi_registry import UpiHandleRegistry, levenshtein


@pytest.fixture
def reg():
    return UpiHandleRegistry()


def test_levenshtein():
    assert levenshtein("", "") == 0
    assert levenshtein("okaxis", "okaxis") == 0
    assert levenshtein("okaxis", "okaxls") == 1
    assert levenshtein("paytm", "paytrn") == 2
    assert levenshtein("abc", "") == 3


@pytest.mark.parametrize("handle", ["okaxis", "OKSBI", "ybl", "paytm", "upi", "hdfcbank", "waicici", "jupiteraxis"])
def test_known_handles(reg, handle):
    assert reg.is_known(handle)
    assert reg.bank_for(handle)


@pytest.mark.parametrize(
    "handle,expected",
    [("okaxls", "okaxis"), ("paytrn", "paytm"), ("okicci", "okicici"), ("okhdfcbnk", "okhdfcbank")],
)
def test_lookalikes_detected(reg, handle, expected):
    look = reg.is_lookalike(handle)
    assert look is not None
    assert look[0] == expected


def test_sbl_is_lookalike_of_short_handle(reg):
    look = reg.is_lookalike("sbl")
    assert look is not None and look[1] == 1


def test_known_handle_is_not_lookalike(reg):
    assert reg.is_lookalike("okaxis") is None


def test_random_handle_is_not_lookalike(reg):
    assert reg.is_lookalike("zzzzqqqq") is None
    assert reg.is_known("zzzzqqqq") is False
