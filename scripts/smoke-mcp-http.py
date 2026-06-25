#!/usr/bin/env python3
import json
import os
import select
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.request
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

    port = find_free_port()
    executable = root / "compose-expose-mcp/build/install/compose-expose-mcp/bin/compose-expose-mcp"
    process = subprocess.Popen(
        [
            str(executable),
            "--project-root",
            str(project_root),
            "--index-file",
            str(index_file),
            "--transport",
            "http",
            "--port",
            str(port),
        ],
        cwd=root,
        stdin=subprocess.DEVNULL,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    client = StreamableHttpJsonRpcClient(f"http://127.0.0.1:{port}/mcp", process)
    try:
        initialize = client.request(
            "initialize",
            {
                "protocolVersion": "2025-06-18",
                "capabilities": {},
                "clientInfo": {"name": "compose-expose-http-smoke", "version": "1.0"},
            },
        )
        require("serverInfo" in initialize, "initialize response missing serverInfo")
        client.notify("notifications/initialized", {})

        tools = client.request("tools/list", {})
        tool_names = [tool.get("name") for tool in tools.get("tools", [])]
        require("search_composables" in tool_names, "search_composables was not listed")
        require("get_composable" in tool_names, "get_composable was not listed")
        require("index_status" in tool_names, "index_status was not listed")

        search = client.request(
            "tools/call",
            {"name": "search_composables", "arguments": {"query": "MetricCard", "limit": 1}},
        )
        search_results = search.get("structuredContent", {}).get("results", [])
        require(len(search_results) == 1, "HTTP search_composables did not return one structured result")
        composable_id = search_results[0].get("id")
        require(isinstance(composable_id, str) and composable_id, "HTTP search_composables result missing id")

        lookup = client.request(
            "tools/call",
            {"name": "get_composable", "arguments": {"id": composable_id}},
        )
        lookup_result = lookup.get("structuredContent", {}).get("result", {})
        require(lookup_result.get("found") is True, "HTTP get_composable did not report the searched id as found")
        require(
            lookup_result.get("composable", {}).get("name") == "MetricCard",
            "HTTP get_composable did not return MetricCard for the searched id",
        )

        status = client.request("tools/call", {"name": "index_status", "arguments": {}})
        status_content = status.get("structuredContent", {}).get("status", {})
        require(status_content.get("exists") is True, "HTTP index_status did not report an existing index")
        require(status_content.get("isStale") is False, "HTTP index_status reported the fresh demo index as stale")

        modules = client.request("resources/read", {"uri": "compose-expose://modules"})
        modules_text = extract_resource_text(modules)
        require("composableCount" in modules_text, "HTTP modules resource missing composableCount")

        print("MCP HTTP smoke passed: initialize, tools/list, search, lookup, status, resources/read.")
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


def extract_resource_text(result: dict) -> str:
    parts = result.get("contents", [])
    return "\n".join(part.get("text", "") for part in parts if isinstance(part, dict))


def find_free_port() -> int:
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


class StreamableHttpJsonRpcClient:
    def __init__(self, url: str, process: subprocess.Popen):
        self.url = url
        self.process = process
        self.next_id = 1

    def request(self, method: str, params: dict) -> dict:
        request_id = self.next_id
        self.next_id += 1
        response = self.post(
            {
                "jsonrpc": "2.0",
                "id": request_id,
                "method": method,
                "params": params,
            },
        )
        if response.get("id") != request_id:
            raise SystemExit(f"Unexpected JSON-RPC id for {method}: {response}")
        if "error" in response:
            raise SystemExit(f"JSON-RPC error for {method}: {response['error']}")
        return response.get("result", {})

    def notify(self, method: str, params: dict) -> None:
        self.post({"jsonrpc": "2.0", "method": method, "params": params}, expect_response=False)

    def post(self, payload: dict, expect_response: bool = True) -> dict:
        body = json.dumps(payload).encode()
        last_error = None
        for _ in range(30):
            if self.process.poll() is not None:
                stderr = self.read_stderr()
                raise SystemExit(f"MCP HTTP process exited with {self.process.returncode}. stderr:\n{stderr}")
            request = urllib.request.Request(
                self.url,
                data=body,
                headers={
                    "Content-Type": "application/json",
                    "Accept": "application/json, text/event-stream",
                },
                method="POST",
            )
            try:
                with urllib.request.urlopen(request, timeout=5) as response:
                    text = response.read().decode()
                    if not expect_response:
                        return {}
                    if not text.strip():
                        raise SystemExit(f"Expected JSON-RPC response, got empty HTTP {response.status}")
                    return decode_http_response(text)
            except urllib.error.URLError as error:
                last_error = error
                time.sleep(0.25)
        stderr = self.read_stderr()
        raise SystemExit(f"Timed out waiting for MCP HTTP response: {last_error}\nstderr:\n{stderr}")

    def read_stderr(self) -> str:
        if self.process.stderr is None:
            return ""
        chunks = []
        fd = self.process.stderr.fileno()
        while True:
            ready, _, _ = select.select([fd], [], [], 0)
            if not ready:
                break
            chunk = os.read(fd, 4096)
            if not chunk:
                break
            chunks.append(chunk)
        return b"".join(chunks).decode(errors="replace")


def decode_http_response(text: str) -> dict:
    stripped = text.strip()
    if stripped.startswith("{"):
        return json.loads(stripped)
    for line in stripped.splitlines():
        if line.startswith("data:"):
            return json.loads(line.removeprefix("data:").strip())
    raise SystemExit(f"Unsupported MCP HTTP response: {text[:500]!r}")


if __name__ == "__main__":
    raise SystemExit(main())
