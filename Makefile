# Preznath — Team Yimberzol
# Common hackathon demo targets. Requires: uv, pnpm, Android SDK (for android-*).

.PHONY: help setup warm dev api-install api-test api-run api-warm web-install web-dev web-test web-build android-test android-apk test

help:
	@echo "Preznath targets:"
	@echo "  make setup        # api-install + web-install + warm models"
	@echo "  make warm         # download HF models into cache"
	@echo "  make dev          # API :8000 + web :5173 (Ctrl-C stops both)"
	@echo "  make api-run      # uvicorn only"
	@echo "  make test         # api + web + android unit tests"
	@echo "  make android-apk  # debug APK"

setup: api-install web-install warm

warm: api-warm

dev:
	@echo "Starting API (:8000) and web (:5173). Ctrl-C stops both."
	@( cd apps/api && . .venv/bin/activate && uvicorn satark.main:app --host 127.0.0.1 --port 8000 ) & \
	API_PID=$$!; \
	( cd apps/web && pnpm dev ); \
	kill $$API_PID 2>/dev/null || true

api-install:
	cd apps/api && uv python install 3.12 && uv venv --python 3.12 && . .venv/bin/activate && uv pip install -e ".[dev]"

api-warm:
	cd apps/api && . .venv/bin/activate && python scripts/warm_models.py

api-run:
	cd apps/api && . .venv/bin/activate && uvicorn satark.main:app --host 127.0.0.1 --port 8000

api-test:
	cd apps/api && . .venv/bin/activate && pytest -q

web-install:
	cd apps/web && pnpm install

web-dev:
	cd apps/web && pnpm dev

web-test:
	cd apps/web && pnpm test

web-build:
	cd apps/web && pnpm build

android-test:
	cd apps/android && ./gradlew testDebugUnitTest

android-apk:
	cd apps/android && ./gradlew assembleDebug
	@echo "APK: apps/android/app/build/outputs/apk/debug/app-debug.apk"

test: api-test web-test android-test
