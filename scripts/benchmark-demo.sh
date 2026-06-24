#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEMO_DIR="$ROOT_DIR/demo"
INDEX_FILE="$DEMO_DIR/build/composeExpose/all-composables.json"
RUNS="${1:-5}"

cd "$DEMO_DIR"

echo "Benchmarking composeExposeAggregateIndex for $RUNS runs"
for run in $(seq 1 "$RUNS"); do
    rm -rf "$DEMO_DIR/build" "$DEMO_DIR/app/build" "$DEMO_DIR/design-system/build" "$DEMO_DIR/feature-dashboard/build"
    /usr/bin/time -p "$ROOT_DIR/gradlew" --no-daemon composeExposeAggregateIndex >/tmp/compose-expose-benchmark-$run.log
done

python3 - "$INDEX_FILE" <<'PY'
import json
import sys
from pathlib import Path

path = Path(sys.argv[1])
data = json.loads(path.read_text())
print(f"Final index contains {len(data.get('composables', []))} composables")
PY
