# Preznath feature audit

Team Yimberzol · Hack for Social Cause (Digital Safety & Cyber Fraud).  
Audit date: 2026-10-27. Presentation `deck/` is **out of scope** for this repo.

Legend: **Done** · **Missing** · **Won't fix / deferred**

## Cross-cutting

| Item | Status | Notes |
| --- | --- | --- |
| Product name Preznath in UI | Done | Web wordmark, Android `app_name`, API `title` |
| Pantone red branding | Done | Web CSS vars `#E4002B`; Android `pantone_red` |
| Root README + how to run | Done | Also `Makefile`, `apps/android/README.md` |
| Makefile scripts | Done | api / web / android targets |
| `deck/` excluded from git | Done | Root `.gitignore` |
| Internal package still named `satark` | Won't fix | Import path + `SATARK_` env prefix; not user-facing |
| Web client class `SatarkApiClient` | Won't fix | Internal TS identifier; UI says Preznath |
| SMS BroadcastReceiver | Deferred | Documented; share intent covers demo |

## API (`apps/api`)

| Item | Status | Notes |
| --- | --- | --- |
| Clean Architecture layers | Done | domain / application / infrastructure / presentation |
| Real HF pipeline (not stubs) | Done | Lazy load + warm script; health shows models |
| `POST /api/analyze` | Done | Verified live: SBI KYC → FRAUD ~96 |
| SSE `POST /api/analyze/stream` | Done | `sse-starlette` EventSourceResponse |
| Tests: entity, fusion, UPI registry | Done | 30 pytest cases |
| Golden end-to-end HF integration tests | Deferred | Needs models in CI; golden covered on Android offline |
| Python package rename to `preznath` | Won't fix | Breaks imports for little demo gain |

## Web (`apps/web`)

| Item | Status | Notes |
| --- | --- | --- |
| MVVM (models / viewmodels / views / services) | Done | |
| Live pipeline UX via SSE | Done | `analyzeStream` + `PipelineTrace` |
| Pantone / Preznath branding | Done | |
| Vitest for viewmodel / SSE parse | Done | 12 tests |
| Per-app README | Won't fix | Covered by root README |

## Android (`apps/android`)

| Item | Status | Notes |
| --- | --- | --- |
| Offline analyzer (heuristics + TF-IDF / ONNX) | Done | Mirrors API stage layout |
| Share intent (`ACTION_SEND` text/plain) | Done | Manifest + `MainActivity` |
| Clean-ish layering (domain / data / ui VM) | Done | |
| Golden message unit tests | Done | KYC / Hindi / dinner |
| Entity / fusion / TF-IDF / handle tests | Done | |
| Debug APK builds | Done | `app/build/outputs/apk/debug/app-debug.apk` (~104 MB, gitignored) |
| Optional ONNX asset in repo | Done | ~22 MB under `assets/models/` |
| Cloud toggle → API with offline fallback | Done | Emulator `10.0.2.2:8000` |

## Critical fixes applied during audit

- Removed orphan package-root `MainActivity.kt` (manifest uses `ui.MainActivity`; signatures diverged).
- Confirmed Android compile + unit tests + `assembleDebug` succeed after concurrent agent fixes to `OfflineAnalyzer` / `OnnxEmbedder`.
- Added root `.gitignore` (excludes `deck/`, secrets, caches, APKs) and `Makefile`.
