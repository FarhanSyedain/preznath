# Preznath — Team Yimberzol

Paste a payment SMS / WhatsApp message. Know if it is a scam **before** you pay.

Hack for Social Cause · VBYLD 2027 · Digital Safety & Cyber Fraud Awareness.

## What ships

| Piece | Path | Role |
| --- | --- | --- |
| FastAPI pipeline | `apps/api` | Clean Architecture + HF models + SSE stage stream |
| React web demo | `apps/web` | Vite + TypeScript MVVM, live pipeline UI |
| Android app | `apps/android` | Offline-first Kotlin Compose (heuristics + TF-IDF / optional ONNX) |

Product name is **Preznath**. Internal Python package folder remains `satark` (import path); all user-facing titles say Preznath.

Feature audit: see [`MISSING.md`](MISSING.md). Common commands: `make help`. Pitch-deck assets are **not** in this repository.

## Architecture

```
Message
  → 1 Normalize & language (XLM-R / script heuristic + hi→en translate)
  → 2 Entity extraction (UPI VPA, URLs, phones, credentials, remote apps)
  → 3 Spam classifier (bert-tiny SMS spam)
  → 4 Phishing classifier (BERT phishing, with smaller fallbacks)
  → 5 Scam intent (DeBERTa zero-shot)
  → 6 Known-scam similarity (MiniLM embeddings vs corpus)
  → 7 Rule signals (UPI-specific red flags)
  → 8 Risk fusion (weighted score + hard floors)
  → SAFE (<30) | SUSPICIOUS (30–65) | FRAUD (>65)
```

Android mirrors the same stage layout offline. Spam / phishing / intent are **honest keyword heuristics** when cloud models are off; similarity uses TF-IDF, upgrading to ONNX MiniLM when the asset loads.

### Risk fusion (deterministic, unit-tested)

`score = Σ(stage_score × weight) / Σ weight × 100`, then three documented adjustments. No LLM, no randomness — the same message always gets the same score.

| Stage | Weight | What moves it |
| --- | --- | --- |
| Entity extraction | 0.15 | unknown / lookalike UPI handle, shortened or `.apk` link, remote-access app |
| Spam classifier | 0.10 | P(spam) |
| Phishing classifier | 0.20 (0.10 if no link / credential in text) | P(phishing) |
| Scam intent | 0.20 | Σ P(label) × label risk (OTP ask 1.0 … normal message 0.0) |
| Known-scam similarity | 0.20 | contrast between nearest scam and nearest legitimate template |
| Rule signals | 0.15 | strongest fired rule + 0.1 per extra rule |
| Language | 0 | informational |

1. **Legitimate-template dampening** — if the closest template is a real bank alert / OTP / friendly note (cosine ≥ 0.75) and no rule or risky entity fired, the score is multiplied by 0.4–0.7. This is why a genuine "Rs 500 debited" SMS stays SAFE even though generic spam/phishing models dislike formal bank language.
2. **Agreement bonus** — three or more stages ≥ 0.70 adds +10.
3. **Hard floors** — lookalike handle, OTP/PIN ask, "approve request to receive money" ⇒ ≥ 70; remote-access app ⇒ ≥ 65.

Full rationale in `apps/api/satark/application/risk_fusion.py`.

## Code structure

**API — Clean Architecture** (`apps/api/satark/`). Dependencies point inward only.

```
domain/          entities, enums, repository ports      — zero framework / ML imports
application/     Stage interface, 7 stages, RiskFusionService, AnalyzeMessageUseCase,
                 model ports (SpamClassifierPort, PhishingClassifierPort, …)
infrastructure/  HF adapters implementing the ports, regex EntityExtractor,
                 UpiHandleRegistry (NPCI handles + Levenshtein lookalikes), scam corpus, settings
presentation/    FastAPI router, pydantic DTOs, domain→DTO mappers, SSE endpoint
main.py          composition root — the only file that knows every layer
```

**Web — MVVM** (`apps/web/src/`). Views never call the API or hold logic.

```
models/       TS types mirroring the API DTOs + the 5 example messages
services/     SatarkApi interface + fetch/SSE client (mockable)
viewmodels/   AnalyzeViewModel — framework-free state machine idle→running→done|error,
              bound to React via useSyncExternalStore; unit-tested with vitest
views/        dumb components: MessageComposer, ExampleChips, PipelineTrace, VerdictCard,
              SignalBreakdown, EntitiesPanel, ReasonsList, AdvicePanel
app/          shell, grid, design tokens (Pantone red + paper/ink)
```

## Models (API)

| Stage | Default Hugging Face id | Notes |
| --- | --- | --- |
| Language | `papluca/xlm-roberta-base-language-detection` | Falls back to Unicode-script heuristic if uncached |
| Translate | `Helsinki-NLP/opus-mt-hi-en` | Hindi Devanagari only |
| Spam | `mrm8488/bert-tiny-finetuned-sms-spam-detection` | |
| Phishing | `ealvaradob/bert-finetuned-phishing` (+ smaller fallbacks) | Prefers first cached candidate |
| Intent | `MoritzLaurer/deberta-v3-xsmall-zeroshot-v1.1-all-33` | |
| Similarity | `sentence-transformers/all-MiniLM-L6-v2` | |

Requires **Python 3.12** (3.14 is too new for the torch wheels we use).

### Quick start (root)

```bash
make setup   # uv venv (py3.12) + pip install, pnpm install, download all models (~3.4 GB once)
make dev     # API on :8000 and web on :5173 together; Ctrl-C stops both
make test    # pytest (30) + vitest (12)
```

Open http://localhost:5173. `/?example=kyc` deep-links straight into a running example (ids: `kyc`, `collect`, `cashback`, `dinner`, `hindi`).

Model downloads use plain HTTP (`HF_HUB_DISABLE_XET=1`); the xet backend stalled on our network. The API process runs with `HF_HUB_OFFLINE=1` so startup never waits on the network — run `make warm` first.

### Manual steps

```bash
cd apps/api
uv python install 3.12
uv venv --python 3.12
source .venv/bin/activate
uv pip install -e ".[dev]"
# optional pre-download (HF cache):
python scripts/warm_models.py
uvicorn satark.main:app --host 127.0.0.1 --port 8000
```

- Health: http://127.0.0.1:8000/api/health  
- Analyze: `POST /api/analyze`  
- SSE stream: `POST /api/analyze/stream`

Env prefix: `SATARK_` (e.g. `SATARK_EAGER_LOAD=false`).

## Web UI

```bash
cd apps/web
pnpm install
pnpm dev          # http://127.0.0.1:5173  (proxies /api → :8000)
pnpm test
pnpm build
```

## Android (offline)

```bash
cd apps/android
./gradlew testDebugUnitTest assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Needs Android SDK (see `local.properties`). Unit tests cover three golden messages without a device. Toggle **Cloud models** in the UI only when the API is reachable (`10.0.2.2:8000` on emulator); failures fall back to offline.

## Demo script (3 minutes)

1. Start API (`:8000`) and wait until `/api/health` says `"status":"ok"`.
2. Start web (`:5173`).
3. Click **SBI KYC block** → Check message. Watch the eight rows light up with latency and score; verdict **FRAUD 96**.
4. Click **Collect-request trick** → **FRAUD 93**. Point at the entities panel: `armyofficer.pay@okaxls` is flagged as a lookalike of `@okaxis`.
5. Click **Hindi electricity scam** → **FRAUD 75**. Show the translated text and the 404 ms translation stage.
6. Click **Friend paying for dinner** → **SAFE 5**. Same models, no false alarm; `priya.s@okicici` validates as ICICI (Google Pay).
7. Click **Pending cashback** → **SUSPICIOUS 62** — the ambiguous middle, where the advice is "confirm through a channel you already trust".
8. Optional: open the Android APK and run the same examples offline.

### Measured verdicts (this machine, Apple Silicon, CPU only)

| Example | Verdict | Score | End-to-end |
| --- | --- | --- | --- |
| SBI KYC block + bit.ly | FRAUD | 96 | ~560 ms |
| Army buyer, "approve request + enter PIN", `@okaxls` | FRAUD | 93 | ~480 ms |
| PhonePe cashback pending, 24 h | SUSPICIOUS | 62 | ~440 ms |
| "sending you 500 for dinner" + `@okicici` | SAFE | 5 | ~410 ms |
| Hindi electricity disconnection (translated) | FRAUD | 75 | ~810 ms |
| Genuine HDFC OTP SMS | SAFE | 19 | ~450 ms |
| Genuine SBI debit alert | SAFE | 13 | ~480 ms |

Zero-shot intent is the slowest stage (~400 ms); every other model answers in under 100 ms.

## Limitations and next steps

- The spam and phishing models were trained on English SMS / email, not Indian payment text; they false-positive on formal bank alerts. The legitimate-template dampening and the UPI-specific rules compensate, but a classifier fine-tuned on Indian UPI scam data is the obvious next model.
- The scam corpus is ~40 templates. Similarity is strong on known patterns and weak on novel ones; growing it from 1930 / cybercrime.gov.in reports would help most.
- Hinglish (romanised Hindi) is passed through untranslated; the zero-shot and similarity stages still catch most of it, but a hi-Latn→en step is missing.
- The UPI handle registry is a curated list, not a live NPCI feed; a payee-name lookup via the UPI "verify VPA" API would turn "unknown handle" into "this VPA does not exist".
- Future: on-device inference (the Android app already runs the heuristics offline; ONNX MiniLM is next), an SMS listener that offers "Check this message" as a notification, and bank / PSP integration so a FRAUD verdict can block a collect request before the PIN screen.

## Honest fallbacks

- Language model missing → script heuristic (still detects Hindi Devanagari).
- Phishing model → walks smaller DistilBERT / MobileBERT / URL-BERT candidates.
- Stage exception → that stage marks `failed`; fusion continues on remaining signals.
- Android without ONNX → TF-IDF similarity; spam/phishing/intent stay heuristic.
- Cloud toggle on Android → on network error, silently uses offline analyzer.

## Android offline app (detail)

Team Yimberzol · Preznath on-device screener lives in `apps/android/`. Full notes: [`apps/android/README.md`](apps/android/README.md).

- **Offline by default:** normalize/language → entities → spam/phishing/intent heuristics → TF-IDF (or ONNX MiniLM INT8 when the ARM64 asset loads) → rules → risk fusion. No runtime model downloads.
- **Bundled:** `scam_corpus.json`, `psp_handles.json`, `models/model_qint8_arm64.onnx` (~22 MB) + `vocab.txt`.
- **Share intent:** WhatsApp/SMS **Share → Preznath** (`ACTION_SEND` / `text/plain`).
- **Cloud (off):** emulator `POST http://10.0.2.2:8000/api/analyze`; failures fall back offline.
- **Build:** `cd apps/android && ./gradlew :app:assembleDebug :app:testDebugUnitTest` → `app/build/outputs/apk/debug/app-debug.apk`.
- **Golden unit tests:** KYC+bit.ly → FRAUD; Devanagari bijli → FRAUD/SUSPICIOUS; dinner transfer → SAFE.
- **SMS BroadcastReceiver:** not shipped (permission weight); documented as next step — local notification only, no exfiltration.
