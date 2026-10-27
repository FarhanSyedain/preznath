"""Helpers to find out which Hugging Face repos are already on disk."""

from __future__ import annotations

from huggingface_hub import try_to_load_from_cache


def is_cached(repo_id: str) -> bool:
    """True if config.json and at least one weight file are in the local HF cache."""
    cfg = try_to_load_from_cache(repo_id, "config.json")
    if not isinstance(cfg, str):
        return False
    for weights in ("model.safetensors", "pytorch_model.bin"):
        w = try_to_load_from_cache(repo_id, weights)
        if isinstance(w, str):
            return True
    return False


def first_cached(candidates: list[str]) -> str | None:
    return next((c for c in candidates if is_cached(c)), None)
