from __future__ import annotations

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="SATARK_", env_file=".env", extra="ignore")

    lang_model: str = "papluca/xlm-roberta-base-language-detection"
    translator_model: str = "Helsinki-NLP/opus-mt-hi-en"
    spam_model: str = "mrm8488/bert-tiny-finetuned-sms-spam-detection"
    # Ordered preference. The first one already in the HF cache is used.
    phishing_models: list[str] = [
        "ealvaradob/bert-finetuned-phishing",  # bert-large, 1.3 GB, best quality
        "cybersectony/phishing-email-detection-distilbert_v2.4.1",  # 268 MB
        "Perth0603/phishing-email-mobilebert",  # 98 MB
        "CrabInHoney/urlbert-tiny-v4-phishing-classifier",  # 15 MB, URL-focused
    ]
    intent_model: str = "MoritzLaurer/deberta-v3-xsmall-zeroshot-v1.1-all-33"
    embedding_model: str = "sentence-transformers/all-MiniLM-L6-v2"

    cors_origins: list[str] = ["http://localhost:5173", "http://127.0.0.1:5173"]
    eager_load: bool = True  # load all models at startup instead of on first request
    hf_offline: bool = True  # never touch the network from the API process; use scripts/warm_models.py
    device: str = "cpu"


settings = Settings()
