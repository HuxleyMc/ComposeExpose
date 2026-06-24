#!/usr/bin/env python3
import json
import subprocess
import sys
from pathlib import Path


ROOT_DIR = Path(__file__).resolve().parents[1]
BENCHMARK = ROOT_DIR / "scripts" / "benchmark-agent-context.py"
TASKS = ROOT_DIR / "evals" / "agent-context-tasks.json"

REQUIRED_METHODS = {
    "with_compose_expose_top_k",
    "without_compose_expose_grep_files",
    "without_compose_expose_full_source",
}


def main() -> int:
    tasks = json.loads(TASKS.read_text())
    if len(tasks) < 10:
        print(f"Expected at least 10 benchmark tasks, found {len(tasks)}", file=sys.stderr)
        return 1

    output = subprocess.check_output(
        [sys.executable, str(BENCHMARK), "--no-build", "--json"],
        cwd=ROOT_DIR,
        text=True,
    )
    results = json.loads(output)
    task_ids = {task["id"] for task in tasks}
    methods_by_task = {task_id: set() for task_id in task_ids}

    for result in results:
        task_id = result.get("task_id")
        if task_id in methods_by_task:
            methods_by_task[task_id].add(result.get("method"))
        if not isinstance(result.get("uses_compose_expose"), bool):
            print(f"Result is missing uses_compose_expose boolean: {result}", file=sys.stderr)
            return 1

    missing = {
        task_id: sorted(REQUIRED_METHODS.difference(methods))
        for task_id, methods in methods_by_task.items()
        if REQUIRED_METHODS.difference(methods)
    }
    if missing:
        print("Benchmark is missing required with/without methods:", file=sys.stderr)
        for task_id, methods in sorted(missing.items()):
            print(f"- {task_id}: {', '.join(methods)}", file=sys.stderr)
        return 1

    totals = {method: 0 for method in REQUIRED_METHODS}
    compose_results = []
    for result in results:
        method = result.get("method")
        if method in totals:
            totals[method] += int(result.get("context_tokens", 0))
        if method == "with_compose_expose_top_k":
            compose_results.append(result)

    misses = sorted(result["task_id"] for result in compose_results if not result.get("hit"))
    if misses:
        print(f"Expected ComposeExpose to hit every benchmark task, missed: {', '.join(misses)}", file=sys.stderr)
        return 1

    reciprocal_ranks = [
        1 / int(result["rank"])
        for result in compose_results
        if result.get("rank") is not None
    ]
    mrr = sum(reciprocal_ranks) / len(tasks)
    if mrr < 0.85:
        print(f"Expected ComposeExpose MRR >= 0.85, got {mrr:.3f}", file=sys.stderr)
        return 1

    compose_tokens = totals["with_compose_expose_top_k"]
    lowest_without_tokens = min(
        totals["without_compose_expose_grep_files"],
        totals["without_compose_expose_full_source"],
    )
    if compose_tokens >= lowest_without_tokens:
        print(
            "Expected ComposeExpose to use fewer total tokens than the best non-ComposeExpose baseline "
            f"({compose_tokens} >= {lowest_without_tokens})",
            file=sys.stderr,
        )
        return 1

    print(f"Verified {len(tasks)} benchmark tasks with explicit with/without ComposeExpose methods.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
