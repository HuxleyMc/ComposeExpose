#!/usr/bin/env python3
import json
import json.decoder
import os
import select
import subprocess
import sys
import time
from pathlib import Path


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    project_root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else root / "demo"
    index_file = Path(sys.argv[2]).resolve() if len(sys.argv) > 2 else project_root / "build/composeExpose/all-composables.json"
    if not index_file.is_file():
        raise SystemExit(f"Index file does not exist: {index_file}")

    subprocess.run(
        [str(root / "gradlew"), "--no-daemon", ":compose-expose-mcp:installDist"],
        cwd=root,
        check=True,
        stdout=subprocess.DEVNULL,
    )

    executable = root / "compose-expose-mcp/build/install/compose-expose-mcp/bin/compose-expose-mcp"
    process = subprocess.Popen(
        [
            str(executable),
            "--project-root",
            str(project_root),
            "--index-file",
            str(index_file),
        ],
        cwd=root,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    client = StdioJsonRpcClient(process)
    try:
        initialize = client.request(
            "initialize",
            {
                "protocolVersion": "2025-06-18",
                "capabilities": {},
                "clientInfo": {"name": "compose-expose-smoke", "version": "1.0"},
            },
        )
        require("serverInfo" in initialize, "initialize response missing serverInfo")
        client.notify("notifications/initialized", {})

        tools = client.request("tools/list", {})
        tool_names = [tool.get("name") for tool in tools.get("tools", [])]
        require("search_composables" in tool_names, "search_composables was not listed")

        search = client.request(
            "tools/call",
            {"name": "search_composables", "arguments": {"query": "MetricCard", "limit": 1}},
        )
        search_text = extract_text_content(search)
        require("MetricCard" in search_text, "search_composables did not return MetricCard")
        search_results = search.get("structuredContent", {}).get("results", [])
        require(len(search_results) == 1, "search_composables did not return one structured result")
        composable_id = search_results[0].get("id")
        require(isinstance(composable_id, str) and composable_id, "search_composables result missing id")

        lookup = client.request(
            "tools/call",
            {"name": "get_composable", "arguments": {"id": composable_id}},
        )
        lookup_result = lookup.get("structuredContent", {}).get("result", {})
        require(lookup_result.get("found") is True, "get_composable did not report the searched id as found")
        require(
            lookup_result.get("composable", {}).get("name") == "MetricCard",
            "get_composable did not return MetricCard for the searched id",
        )

        missing_lookup = client.request(
            "tools/call",
            {"name": "get_composable", "arguments": {"id": ":missing:main:dev.example.MissingCard#"}},
        )
        missing_result = missing_lookup.get("structuredContent", {}).get("result", {})
        require(missing_result.get("found") is False, "get_composable did not report an unknown id as missing")
        require(
            "search_composables" in (missing_result.get("message") or ""),
            "get_composable miss did not include search recovery guidance",
        )

        status = client.request("tools/call", {"name": "index_status", "arguments": {}})
        status_content = status.get("structuredContent", {}).get("status", {})
        require(status_content.get("exists") is True, "index_status did not report an existing index")
        require(status_content.get("isStale") is False, "index_status reported the fresh demo index as stale")
        require(":app" in status_content.get("modules", []), "index_status missing :app module")

        previews = client.request(
            "tools/call",
            {"name": "list_previews", "arguments": {"annotation": "Preview", "limit": 2}},
        )
        preview_items = previews.get("structuredContent", {}).get("previews", [])
        require(0 < len(preview_items) <= 2, "list_previews did not return a bounded preview list")

        invalid_limit = client.request(
            "tools/call",
            {"name": "search_composables", "arguments": {"query": "MetricCard", "limit": 0}},
        )
        require(invalid_limit.get("isError") is True, "invalid search_composables limit was not a tool error")
        invalid_limit_text = extract_text_content(invalid_limit)
        require(
            "expected integer from 1 to 100" in invalid_limit_text,
            "invalid search_composables limit returned an unexpected error message",
        )

        modules = client.request("resources/read", {"uri": "compose-expose://modules"})
        modules_text = extract_resource_text(modules)
        require("composableCount" in modules_text, "modules resource missing composableCount")
        require("sourceSets" in modules_text, "modules resource missing sourceSets")

        print("MCP stdio smoke passed: initialize, tools/list, search, lookup, status, previews, tool errors, resources/read.")
        return 0
    finally:
        process.terminate()
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=5)


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


def extract_text_content(result: dict) -> str:
    parts = result.get("content", [])
    return "\n".join(part.get("text", "") for part in parts if isinstance(part, dict))


def extract_resource_text(result: dict) -> str:
    parts = result.get("contents", [])
    return "\n".join(part.get("text", "") for part in parts if isinstance(part, dict))


class StdioJsonRpcClient:
    def __init__(self, process: subprocess.Popen):
        if process.stdin is None or process.stdout is None:
            raise ValueError("Process must have stdin and stdout pipes")
        self.process = process
        self.stdin = process.stdin
        self.stdout = process.stdout
        self.buffer = b""
        self.next_id = 1

    def request(self, method: str, params: dict) -> dict:
        request_id = self.next_id
        self.next_id += 1
        self._send({"jsonrpc": "2.0", "id": request_id, "method": method, "params": params})
        deadline = time.monotonic() + 30
        while True:
            message = self._read_message(deadline)
            if message.get("id") != request_id:
                continue
            if "error" in message:
                raise SystemExit(f"{method} failed: {message['error']}")
            return message.get("result", {})

    def notify(self, method: str, params: dict) -> None:
        self._send({"jsonrpc": "2.0", "method": method, "params": params})

    def _send(self, payload: dict) -> None:
        body = json.dumps(payload, separators=(",", ":")).encode("utf-8")
        self.stdin.write(body + b"\n")
        self.stdin.flush()

    def _read_message(self, deadline: float) -> dict:
        while b"\n" not in self.buffer:
            self._read_more(deadline)
        line, self.buffer = self.buffer.split(b"\n", 1)
        if not line.strip():
            return self._read_message(deadline)
        text = line.decode("utf-8", errors="replace")
        try:
            return json.loads(text)
        except json.decoder.JSONDecodeError:
            if text.startswith("> ") or text.startswith("BUILD ") or text.startswith("To honour ") or text.startswith("kotlin-logging:"):
                return self._read_message(deadline)
            stderr = read_available(self.process.stderr)
            raise SystemExit(f"Received non-JSON stdout line from MCP process: {text!r}\nstderr:\n{stderr}")

    def _read_more(self, deadline: float) -> None:
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            stderr = read_available(self.process.stderr)
            raise SystemExit(f"Timed out waiting for MCP response. stderr:\n{stderr}")
        ready, _, _ = select.select([self.stdout], [], [], min(remaining, 1.0))
        if not ready:
            if self.process.poll() is not None:
                stderr = read_available(self.process.stderr)
                raise SystemExit(f"MCP process exited with {self.process.returncode}. stderr:\n{stderr}")
            return
        chunk = os.read(self.stdout.fileno(), 8192)
        if not chunk:
            stderr = read_available(self.process.stderr)
            raise SystemExit(f"MCP stdout closed. stderr:\n{stderr}")
        self.buffer += chunk


def read_available(pipe) -> str:
    if pipe is None:
        return ""
    chunks = []
    while True:
        ready, _, _ = select.select([pipe], [], [], 0)
        if not ready:
            break
        chunk = os.read(pipe.fileno(), 8192)
        if not chunk:
            break
        chunks.append(chunk)
    return b"".join(chunks).decode("utf-8", errors="replace")


if __name__ == "__main__":
    raise SystemExit(main())
