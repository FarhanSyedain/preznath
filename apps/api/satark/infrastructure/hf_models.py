"""Hugging Face adapters implementing the application ports.

Every adapter loads its model lazily on first use and caches it on the
instance, so the composition root can construct them cheaply and decide when
to warm them (eagerly at startup, by default).
"""

from __future__ import annotations

import logging
import os
import threading
from functools import cached_property

os.environ.setdefault("TOKENIZERS_PARALLELISM", "false")
os.environ.setdefault("HF_HUB_DISABLE_XET", "1")

import numpy as np  # noqa: E402
import torch  # noqa: E402

from satark.application.ports import (  # noqa: E402
    IntentClassifierPort,
    LanguageDetectorPort,
    PhishingClassifierPort,
    ScamSimilarityPort,
    SimilarMatch,
    SpamClassifierPort,
    TranslatorPort,
)

from .scam_corpus import CORPUS  # noqa: E402

log = logging.getLogger("satark.models")
torch.set_num_threads(max(1, (os.cpu_count() or 4) // 2))


class _Loadable:
    """Mixin: tracks whether the heavy model object has been created."""

    model_id: str
    _lock = threading.Lock()

    @property
    def loaded(self) -> bool:
        return "pipe" in self.__dict__ or "model" in self.__dict__

    def warm(self) -> None:
        with self._lock:
            getattr(self, "pipe", None)
            getattr(self, "model", None)


def _positive_probability(scores: list[dict], positive_tokens: tuple[str, ...]) -> float:
    """Pick the probability of the 'bad' class regardless of how the repo named its labels."""
    for s in scores:
        label = str(s["label"]).lower()
        if any(tok in label for tok in positive_tokens):
            return float(s["score"])
    return 0.0


class HfLanguageDetector(_Loadable, LanguageDetectorPort):
    """XLM-R language ID when cached; otherwise a Unicode-script heuristic.

    The transformer is ~1.1 GB, so we never pull it on the request path. Run
    scripts/warm_models.py to fetch it; the adapter upgrades itself on the
    next startup.
    """

    def __init__(self, model_id: str, device: str = "cpu", allow_download: bool = False):
        self.model_id = model_id
        self._device = device
        self._allow_download = allow_download
        self.active_model_id = model_id

    @cached_property
    def pipe(self):
        from transformers import pipeline

        from .model_cache import is_cached
        from .script_language_detector import ScriptLanguageDetector

        if is_cached(self.model_id) or self._allow_download:
            try:
                log.info("loading language detector %s", self.model_id)
                return pipeline("text-classification", model=self.model_id, device=self._device, top_k=1)
            except Exception as exc:  # noqa: BLE001
                log.warning("language model %s failed: %s", self.model_id, exc)
        log.warning("language model %s not cached; using unicode-script heuristic", self.model_id)
        fallback = ScriptLanguageDetector()
        self.active_model_id = fallback.model_id
        return fallback

    def detect(self, text: str) -> tuple[str, float]:
        from .script_language_detector import ScriptLanguageDetector

        if isinstance(self.pipe, ScriptLanguageDetector):
            return self.pipe.detect(text)
        out = self.pipe(text[:512], truncation=True)
        best = out[0][0] if isinstance(out[0], list) else out[0]
        return str(best["label"]).lower(), float(best["score"])


class HfTranslator(_Loadable, TranslatorPort):
    def __init__(self, model_id: str, device: str = "cpu"):
        self.model_id = model_id
        self._device = device

    @cached_property
    def model(self):
        from transformers import AutoModelForSeq2SeqLM, AutoTokenizer

        log.info("loading translator %s", self.model_id)
        tok = AutoTokenizer.from_pretrained(self.model_id)
        mdl = AutoModelForSeq2SeqLM.from_pretrained(self.model_id).to(self._device).eval()
        return tok, mdl

    def translate(self, text: str) -> str:
        tok, mdl = self.model
        # Translate sentence by sentence; Marian models degrade on long inputs.
        import re

        sentences = [s for s in re.split(r"(?<=[।\.!?])\s+", text.strip()) if s]
        out: list[str] = []
        with torch.inference_mode():
            for chunk in sentences or [text]:
                batch = tok([chunk], return_tensors="pt", truncation=True, max_length=256).to(self._device)
                gen = mdl.generate(**batch, max_new_tokens=128, num_beams=2)
                out.append(tok.batch_decode(gen, skip_special_tokens=True)[0].strip())
        return " ".join(out)


class HfSpamClassifier(_Loadable, SpamClassifierPort):
    def __init__(self, model_id: str, device: str = "cpu"):
        self.model_id = model_id
        self._device = device

    @cached_property
    def pipe(self):
        from transformers import pipeline

        log.info("loading spam classifier %s", self.model_id)
        return pipeline("text-classification", model=self.model_id, device=self._device, top_k=None)

    def spam_probability(self, text: str) -> float:
        scores = self.pipe(text[:512], truncation=True)[0]
        return _positive_probability(scores, ("spam", "label_1"))


class HfPhishingClassifier(_Loadable, PhishingClassifierPort):
    """Walks an ordered list of candidate models.

    Prefers the first candidate that is already in the local cache (so a slow
    network never blocks startup); if none is cached it downloads the first.
    On any load failure it moves on to the next candidate.
    """

    def __init__(self, candidates: list[str], device: str = "cpu"):
        self.candidates = candidates
        self.model_id = candidates[0]
        self._device = device
        self.active_model_id = candidates[0]

    @cached_property
    def pipe(self):
        from transformers import pipeline

        from .model_cache import first_cached

        cached = first_cached(self.candidates)
        order = ([cached] if cached else []) + [c for c in self.candidates if c != cached]
        last: Exception | None = None
        for mid in order:
            try:
                log.info("loading phishing classifier %s", mid)
                p = pipeline("text-classification", model=mid, device=self._device, top_k=None)
                self.active_model_id = mid
                return p
            except Exception as exc:  # noqa: BLE001
                log.warning("phishing model %s failed: %s", mid, exc)
                last = exc
        raise RuntimeError(f"no phishing model could be loaded: {last}")

    def phishing_probability(self, text: str) -> float:
        scores = self.pipe(text[:512], truncation=True)[0]
        return _positive_probability(scores, ("phish", "label_1", "malicious", "spam", "unsafe"))


class HfZeroShotIntentClassifier(_Loadable, IntentClassifierPort):
    def __init__(self, model_id: str, device: str = "cpu"):
        self.model_id = model_id
        self._device = device

    @cached_property
    def pipe(self):
        from transformers import pipeline

        log.info("loading zero-shot classifier %s", self.model_id)
        return pipeline("zero-shot-classification", model=self.model_id, device=self._device)

    def classify(self, text: str, labels: list[str]) -> list[tuple[str, float]]:
        out = self.pipe(
            text[:512],
            candidate_labels=labels,
            hypothesis_template="This message is a {}.",
            multi_label=False,
        )
        return list(zip(out["labels"], (float(s) for s in out["scores"])))


class SentenceTransformerScamIndex(_Loadable, ScamSimilarityPort):
    def __init__(self, model_id: str, device: str = "cpu"):
        self.model_id = model_id
        self._device = device
        self._corpus = CORPUS

    @cached_property
    def model(self):
        from sentence_transformers import SentenceTransformer

        log.info("loading sentence embeddings %s", self.model_id)
        return SentenceTransformer(self.model_id, device=self._device)

    @cached_property
    def _matrix(self) -> np.ndarray:
        texts = [t for t, _, _ in self._corpus]
        return self.model.encode(texts, normalize_embeddings=True, convert_to_numpy=True)

    def warm(self) -> None:
        super().warm()
        _ = self._matrix

    def nearest(self, text: str, k: int = 3) -> list[SimilarMatch]:
        q = self.model.encode([text], normalize_embeddings=True, convert_to_numpy=True)[0]
        sims = self._matrix @ q
        idx = np.argsort(-sims)[:k]
        return [
            SimilarMatch(text=self._corpus[i][0], category=self._corpus[i][1], is_scam=self._corpus[i][2], similarity=float(sims[i]))
            for i in idx
        ]
