"""Pre-download every model the pipeline uses so the demo never waits on the network.

Usage:  .venv/bin/python scripts/warm_models.py
"""

from __future__ import annotations

import os
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
os.environ.setdefault("TOKENIZERS_PARALLELISM", "false")
# The xet transfer backend stalls on some networks; plain HTTP is reliable.
os.environ.setdefault("HF_HUB_DISABLE_XET", "1")

from huggingface_hub import snapshot_download  # noqa: E402

from satark.infrastructure.config import settings  # noqa: E402


def size_of(path: str) -> float:
    total = 0
    for root, _, files in os.walk(path):
        for f in files:
            p = os.path.join(root, f)
            if os.path.islink(p):
                p = os.path.realpath(p)
            if os.path.exists(p):
                total += os.path.getsize(p)
    return total / 1e6


def warm(repo: str) -> bool:
    t = time.perf_counter()
    try:
        path = snapshot_download(
            repo,
            allow_patterns=["*.json", "*.txt", "*.model", "*.safetensors", "*.spm", "*.bpe", "*.py", "spiece.*", "sentencepiece.*"],
            ignore_patterns=["*.bin", "*.h5", "*.msgpack", "*.ot", "onnx/*", "openvino/*", "*.onnx"],
        )
        # Some repos only ship pytorch_model.bin — fetch it if no safetensors landed.
        if not any(f.endswith(".safetensors") for f in os.listdir(path)):
            path = snapshot_download(repo, allow_patterns=["*.json", "*.txt", "*.model", "*.bin", "*.spm", "spiece.*"])
        mb = size_of(path)
        print(f"  ok   {repo:<62} {mb:7.0f} MB  {time.perf_counter() - t:5.1f}s", flush=True)
        return True
    except Exception as exc:  # noqa: BLE001
        print(f"  FAIL {repo}: {exc}", flush=True)
        return False


if __name__ == "__main__":
    # Small models first so a slow network still leaves a working pipeline.
    repos = [
        settings.spam_model,
        settings.embedding_model,
        settings.intent_model,
        settings.translator_model,
        *reversed(settings.phishing_models),  # tiny -> large
        settings.lang_model,
    ]
    only = sys.argv[1:]  # optional: warm a subset
    print("Warming models into the Hugging Face cache…")
    for r in repos:
        if only and r not in only:
            continue
        warm(r)
    print("done.")
