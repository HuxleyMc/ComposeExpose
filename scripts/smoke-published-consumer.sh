#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_DIR="$ROOT_DIR/build/local-maven"
FIXTURE_DIR="$ROOT_DIR/fixtures/published-consumer"
INDEX_FILE="$FIXTURE_DIR/build/composeExpose/all-composables.json"

rm -rf "$REPO_DIR"
"$ROOT_DIR/gradlew" --no-daemon publishAllPublicationsToLocalTestRepository

cd "$FIXTURE_DIR"
"$ROOT_DIR/gradlew" --no-daemon \
    -p "$FIXTURE_DIR" \
    -PcomposeExposeRepo="$REPO_DIR" \
    composeExposeAggregateIndex \
    :ui:verifyComposeExposeProcessorResolution

python3 - "$INDEX_FILE" <<'PY'
import json
import sys
from pathlib import Path

path = Path(sys.argv[1])
data = json.loads(path.read_text())
names = {item["name"] for item in data.get("composables", [])}
expected = {"PublishedCard", "PublishedCardPreview"}
missing = expected - names
if missing:
    raise SystemExit(f"Missing expected composables: {sorted(missing)}")
print(f"Published consumer indexed {len(names)} composables: {', '.join(sorted(names))}")
PY
