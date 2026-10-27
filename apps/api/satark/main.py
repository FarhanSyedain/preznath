"""Composition root. The only place that knows about every layer at once."""

from __future__ import annotations

import logging
import os
import threading
import time
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from satark.infrastructure.config import settings

if settings.hf_offline:
    os.environ.setdefault("HF_HUB_OFFLINE", "1")
    os.environ.setdefault("TRANSFORMERS_OFFLINE", "1")

from satark.application import AnalyzeMessageUseCase, RiskFusionService  # noqa: E402
from satark.application.stages import (  # noqa: E402
    EntityExtractionStage,
    IntentStage,
    NormalizeLanguageStage,
    PhishingStage,
    RuleSignalsStage,
    SimilarityStage,
    SpamStage,
)
from satark.infrastructure.entity_extractor import RegexEntityExtractor  # noqa: E402
from satark.infrastructure.hf_models import (  # noqa: E402
    HfLanguageDetector,
    HfPhishingClassifier,
    HfSpamClassifier,
    HfTranslator,
    HfZeroShotIntentClassifier,
    SentenceTransformerScamIndex,
)
from satark.infrastructure.upi_registry import UpiHandleRegistry  # noqa: E402
from satark.presentation.router import router  # noqa: E402

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
log = logging.getLogger("satark")


def build_models() -> dict[str, object]:
    d = settings.device
    return {
        "language_detector": HfLanguageDetector(settings.lang_model, d),
        "translator": HfTranslator(settings.translator_model, d),
        "spam": HfSpamClassifier(settings.spam_model, d),
        "phishing": HfPhishingClassifier(settings.phishing_models, d),
        "intent": HfZeroShotIntentClassifier(settings.intent_model, d),
        "similarity": SentenceTransformerScamIndex(settings.embedding_model, d),
    }


def build_use_case(models: dict[str, object]) -> AnalyzeMessageUseCase:
    registry = UpiHandleRegistry()
    extractor = RegexEntityExtractor(registry)
    stages = [
        NormalizeLanguageStage(models["language_detector"], models["translator"]),  # type: ignore[arg-type]
        EntityExtractionStage(extractor),
        SpamStage(models["spam"]),  # type: ignore[arg-type]
        PhishingStage(models["phishing"]),  # type: ignore[arg-type]
        IntentStage(models["intent"]),  # type: ignore[arg-type]
        SimilarityStage(models["similarity"]),  # type: ignore[arg-type]
        RuleSignalsStage(),
    ]
    return AnalyzeMessageUseCase(stages, RiskFusionService())


def warm_all(models: dict[str, object]) -> None:
    for name, m in models.items():
        t = time.perf_counter()
        try:
            m.warm()  # type: ignore[attr-defined]
            log.info("model %-18s ready in %5.1fs (%s)", name, time.perf_counter() - t, getattr(m, "active_model_id", m.model_id))  # type: ignore[attr-defined]
        except Exception:  # noqa: BLE001
            log.exception("model %s failed to load", name)


@asynccontextmanager
async def lifespan(app: FastAPI):
    models = build_models()
    app.state.models = models
    app.state.use_case = build_use_case(models)
    if settings.eager_load:
        # Load in a background thread so /api/health answers immediately.
        threading.Thread(target=warm_all, args=(models,), daemon=True, name="model-warmup").start()
    yield


def create_app() -> FastAPI:
    app = FastAPI(title="Preznath API", version="0.1.0", lifespan=lifespan)
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins,
        allow_methods=["*"],
        allow_headers=["*"],
    )
    app.include_router(router)
    return app


app = create_app()
