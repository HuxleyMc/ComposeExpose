#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT_ROOT="${1:-$ROOT_DIR/demo}"
INDEX_FILE="${2:-$PROJECT_ROOT/build/composeExpose/all-composables.json}"

python3 "$ROOT_DIR/scripts/smoke-mcp-http.py" "$PROJECT_ROOT" "$INDEX_FILE"
