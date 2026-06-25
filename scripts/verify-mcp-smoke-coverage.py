#!/usr/bin/env python3
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SMOKE = ROOT / "scripts" / "smoke-mcp-stdio.py"

REQUIRED_SNIPPETS = {
    "get_composable tool call": '"name": "get_composable"',
    "lookup miss assertion": '"found") is False',
    "index_status tool call": '"name": "index_status"',
    "refresh_index tool call": '"name": "refresh_index"',
    "refresh success assertion": '"success") is True',
    "list_previews tool call": '"name": "list_previews"',
    "tool error assertion": "isError",
    "invalid limit coverage": "expected integer from 1 to 100",
}


def main() -> int:
    text = SMOKE.read_text()
    missing = [name for name, snippet in REQUIRED_SNIPPETS.items() if snippet not in text]
    if missing:
        raise SystemExit("Missing MCP smoke coverage: " + ", ".join(missing))
    print("Verified MCP stdio smoke covers lookup, status, refresh, previews, and tool errors.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
