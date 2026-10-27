# Preznath — Team Yimberzol
# Common hackathon demo targets. Requires: uv, pnpm, Android SDK (for android-*).

.PHONY: help api-install api-test api-run api-warm web-install web-dev web-test web-build android-test android-apk test

help:
	@echo "Preznath targets:"
	@echo "  make api-install   # uv venv + editable install (Python 3.12)"
	@echo "  make api-warm      # download HF models into cache"
	@echo "  make api-run       # uvicorn on :8000"
	@echo "  make api-test      # pytest"
	@echo "  make web-install  # pnpm install"
	@echo "  make web-dev      # Vite on :5173 (proxies /api → :8000)"
	@echo "  make web-test     # vitest"
	@echo "  make web-build    # production build"
	@echo "  make android-test # JVM unit tests"
	@echo "  make android-apk  # debug APK"
	@echo "  make test         # api + web + android unit tests"

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
