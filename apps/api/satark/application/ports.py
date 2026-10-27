"""Ports the application layer depends on. Infrastructure implements them.

Each port is deliberately tiny: a single method returning plain Python
values, so adapters can be swapped (or faked in tests) without touching any
pipeline logic.
"""

from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass

from satark.domain import Entity


class LanguageDetectorPort(ABC):
    @abstractmethod
    def detect(self, text: str) -> tuple[str, float]:
        """Return (ISO-639-1 code, confidence 0..1)."""


class TranslatorPort(ABC):
    @abstractmethod
    def translate(self, text: str) -> str: ...


class SpamClassifierPort(ABC):
    @abstractmethod
    def spam_probability(self, text: str) -> float: ...


class PhishingClassifierPort(ABC):
    @abstractmethod
    def phishing_probability(self, text: str) -> float: ...


class IntentClassifierPort(ABC):
    @abstractmethod
    def classify(self, text: str, labels: list[str]) -> list[tuple[str, float]]:
        """Return (label, probability) pairs sorted by probability, desc."""


@dataclass(frozen=True)
class SimilarMatch:
    text: str
    category: str
    is_scam: bool
    similarity: float


class ScamSimilarityPort(ABC):
    @abstractmethod
    def nearest(self, text: str, k: int = 3) -> list[SimilarMatch]: ...


class EntityExtractorPort(ABC):
    @abstractmethod
    def extract(self, text: str) -> list[Entity]: ...
