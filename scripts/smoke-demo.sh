#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEMO_DIR="$ROOT_DIR/demo"
INDEX_FILE="$DEMO_DIR/build/composeExpose/all-composables.json"

cd "$DEMO_DIR"
"$ROOT_DIR/gradlew" --no-daemon composeExposeAggregateIndex

python3 - "$INDEX_FILE" <<'PY'
import json
import sys
from pathlib import Path

path = Path(sys.argv[1])
data = json.loads(path.read_text())
count = len(data.get("composables", []))
modules = data.get("metadata", {}).get("modules", [])
if count < 5:
    raise SystemExit(f"Expected at least 5 composables, found {count}")
print(f"Indexed {count} composables from {len(modules)} modules: {', '.join(modules)}")
PY

cd "$ROOT_DIR"
"$ROOT_DIR/gradlew" --no-daemon :compose-expose-mcp:run --args="--help" >/dev/null
echo "MCP CLI help command runs."
