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
names = {item.get("name") for item in data.get("composables", [])}
required_names = {
    "DemoApp",
    "DemoTheme",
    "MetricCard",
    "DashboardRoute",
    "DashboardScreen",
}
missing_names = sorted(required_names - names)
if missing_names:
    raise SystemExit(f"Missing required demo composables: {', '.join(missing_names)}")
variant_badges = [
    item for item in data.get("composables", [])
    if item.get("module") == ":app" and item.get("name") == "VariantBadge"
]
variant_source_sets = sorted(item.get("sourceSet") for item in variant_badges)
if variant_source_sets != ["free", "paid"]:
    raise SystemExit(f"Expected free and paid VariantBadge entries, found {variant_source_sets}")
print(f"Indexed {count} composables from {len(modules)} modules: {', '.join(modules)}")
PY

cd "$ROOT_DIR"
"$ROOT_DIR/scripts/smoke-mcp-stdio.sh" "$DEMO_DIR" "$INDEX_FILE"
