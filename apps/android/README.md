# Preznath — Android (offline-first)

Team **Yimberzol** · Hack for Social Cause · Digital Safety & Cyber Fraud Awareness

Paste or share an SMS / WhatsApp message. Preznath returns **SAFE / SUSPICIOUS / FRAUD** with score, reasons, and advice — **fully offline**, no network required for analysis.

## How it fits the stack

| Surface | Role |
|---------|------|
| `apps/api` | Python FastAPI pipeline (BERT / DeBERTa / SSE trace) |
| `apps/web` | Browser UI with live stage trace |
| `apps/android` | On-device offline screener + optional cloud sync |
| `deck/` | Pitch deck |

The Android app mirrors the server’s stage layout and risk-fusion thresholds (`<30` SAFE, `30–65` SUSPICIOUS, `>65` FRAUD) so demos feel consistent across web and mobile.

## Offline vs cloud

- **Offline (default):** runs entirely on-device. No downloads at user runtime.
- **Cloud models (off by default):** when enabled and the host API is up, `POST http://10.0.2.2:8000/api/analyze` with `{ "text", "upi_id", "url" }`. If cloud fails, the app falls back to offline automatically.

## Bundled assets & models

```
app/src/main/assets/
  scam_corpus.json          ~40 scam + ~20 legit templates
  psp_handles.json          NPCI PSP / bank handle sample
  models/
    model_qint8_arm64.onnx  all-MiniLM-L6-v2 INT8 (~22 MB) — optional embeddings
    vocab.txt               WordPiece vocab for MiniLM
```

**Similarity engine**

1. Prefer **ONNX Runtime Mobile** + MiniLM INT8 when the session loads (typical on ARM64 devices/emulators).
2. If ONNX is missing, wrong ABI (e.g. x86_64 emulator), or fails to init → **TF-IDF + cosine** over the same corpus. Unit tests always use TF-IDF.

Other offline stages (always on):

- Normalize + Devanagari / Hinglish language heuristic  
- Regex entity extraction (VPA, phones, URLs, amounts, OTP/PIN/CVV, banks, AnyDesk…)  
- NPCI handle registry + Levenshtein lookalikes  
- Rule signals (urgency, collect-request trick, remote apps, APK, shorteners, bank/handle mismatch)  
- Keyword heuristics for spam / phishing / intent (honest stand-ins for cloud BERT — evidence text says so)  
- Deterministic risk fusion with hard floors + 1930 / cybercrime.gov.in advice  

## Open in Android Studio

1. Install Android Studio (Ladybug+), SDK 35, a device or emulator (API 26+).
2. **File → Open** → `apps/android/`.
3. Let Gradle sync. `local.properties` should point at your SDK (`sdk.dir=…`).
4. Run configuration: `app` → Debug.

CLI (with SDK installed):

```bash
export ANDROID_HOME=~/Library/Android/sdk   # or your SDK path
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
cd apps/android
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

## Share a message into Preznath

1. In WhatsApp / Messages / Chrome, open a suspicious text.
2. **Share → Preznath** (`ACTION_SEND` / `text/plain`).
3. The message lands in the paste field — tap **ANALYZE**.

SMS auto-listener (`RECEIVE_SMS`) is intentionally not shipped for this demo (permission weight). Next step: a BroadcastReceiver that only posts a local notification “Check this message in Preznath” with no exfiltration.

## Demo script (3 minutes)

1. Open Preznath on the emulator. Confirm **Cloud models** is unchecked.
2. Tap example **Fake SBI KYC** → **ANALYZE**. Watch stages 01–08. Verdict: **FRAUD**.
3. Tap **Hindi bijli** → **ANALYZE**. Verdict: **FRAUD** or **SUSPICIOUS**.
4. Tap **Dinner transfer** → **ANALYZE**. Verdict: **SAFE** (ink, not green).
5. Optional: from the host machine, share text into the emulator via `adb`, or enable Cloud models with FastAPI on `:8000`.

## Architecture

Clean Architecture + MVVM (Kotlin, Jetpack Compose):

```
domain/        entities, Verdict, AnalyzerPort
application/   AnalyzeMessageUseCase, RiskFusion
data/          OfflineAnalyzer, CloudAnalyzer, extractors, TF-IDF / ONNX
ui/            theme, AnalyzeScreen, ResultScreen, PipelineTrace, ViewModel
```

## Branding

Pantone red `#E4002B` + neutrals only. SAFE uses ink `#121212` — never green. Editorial / report UI; no gradients, glass, or emoji.
