#!/usr/bin/env bash
set -euo pipefail

# Where to write the archive (into the current running folder)
OUT="$(pwd)/${1:-halacha-engine_sources_$(date +%Y%m%d_%H%M).zip}"

# Root of the repo
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "$ROOT"

# Create a temp staging dir
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT

# Copy only relevant bits
rsync -a --prune-empty-dirs \
  --include='/build.gradle.kts' \
  --include='/settings.gradle.kts' \
  --include='/gradle.properties' \
  --include='/gradlew' \
  --include='/gradlew.bat' \
  --include='/gradle/wrapper/***' \
  --include='/README.md' \
  --include='/core-engine/***' \
  --include='/profiles/***' \
  --include='/rest-api/***' \
  --include='/android-demo/***' \
  --exclude='/**' \
  ./ "$STAGE/"

# Clean out build artifacts and IDE files if they slipped in
find "$STAGE" -name build -type d -prune -exec rm -rf {} +
find "$STAGE" -name .gradle -type d -prune -exec rm -rf {} +
find "$STAGE" -name .idea -type d -prune -exec rm -rf {} +
find "$STAGE" -name .kotlin -type d -prune -exec rm -rf {} +

# Zip it
cd "$STAGE"
zip -r "$OUT" . > /dev/null

echo "Packed sources -> $OUT"

