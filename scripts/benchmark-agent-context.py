#!/usr/bin/env python3
import argparse
import json
import math
import re
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Optional


STOPWORDS = {
    "a", "an", "and", "are", "by", "for", "from", "in", "is", "of", "or", "plus",
    "the", "that", "to", "used", "with", "which",
    "accepts", "composable", "displaying", "find", "preview", "reusable",
}


@dataclass
class BenchmarkResult:
    task_id: str
    method: str
    context_tokens: int
    hit: bool
    rank: Optional[int]


def main() -> None:
    parser = argparse.ArgumentParser(description="Benchmark ComposeExpose agent context usefulness and token size.")
    parser.add_argument("--demo-dir", default="demo", type=Path)
    parser.add_argument("--tasks", default="evals/agent-context-tasks.json", type=Path)
    parser.add_argument("--index", default=None, type=Path)
    parser.add_argument("--top-k", default=3, type=int)
    parser.add_argument("--no-build", action="store_true")
    parser.add_argument("--json", action="store_true", help="Print raw JSON results.")
    args = parser.parse_args()

    root = Path(__file__).resolve().parents[1]
    demo_dir = (root / args.demo_dir).resolve()
    index_path = (args.index or demo_dir / "build/composeExpose/all-composables.json").resolve()

    if not args.no_build:
        subprocess.run(
            [str(root / "gradlew"), "--no-daemon", "composeExposeAggregateIndex"],
            cwd=demo_dir,
            check=True,
            stdout=subprocess.DEVNULL,
        )

    tasks = json.loads((root / args.tasks).read_text())
    index = json.loads(index_path.read_text())
    source_files = sorted(
        path for path in demo_dir.rglob("*.kt")
        if "/build/" not in path.as_posix()
    )
    source_by_file = {path: path.read_text() for path in source_files}
    composables = index.get("composables", [])

    results: list[BenchmarkResult] = []
    for task in tasks:
        expected = set(task["expected"])
        ranked = rank_composables(task["prompt"], composables)
        top_records = [record for _, record in ranked[: args.top_k]]
        index_context = render_index_context(top_records)
        results.append(
            BenchmarkResult(
                task_id=task["id"],
                method="compose_expose_top_k",
                context_tokens=count_tokens(index_context),
                hit=contains_expected([record["name"] for record in top_records], expected),
                rank=first_rank([record["name"] for _, record in ranked], expected),
            )
        )

        grep_files = grep_source_files(task["prompt"], source_by_file)
        grep_context = render_source_context(grep_files)
        grep_names = composable_names_in_context(grep_context, expected)
        results.append(
            BenchmarkResult(
                task_id=task["id"],
                method="grep_matching_files",
                context_tokens=count_tokens(grep_context),
                hit=contains_expected(grep_names, expected),
                rank=None,
            )
        )

        source_context = render_source_context(source_by_file)
        source_names = composable_names_in_context(source_context, expected)
        results.append(
            BenchmarkResult(
                task_id=task["id"],
                method="full_source_dump",
                context_tokens=count_tokens(source_context),
                hit=contains_expected(source_names, expected),
                rank=None,
            )
        )

    if args.json:
        print(json.dumps([result.__dict__ for result in results], indent=2))
    else:
        print_report(results)


def rank_composables(prompt: str, composables: list[dict]) -> list[tuple[float, dict]]:
    query_tokens = tokenize(prompt)
    ranked = []
    for record in composables:
        searchable = {
            "name": split_camel(record.get("name", "")),
            "package": tokenize(record.get("packageName", "")),
            "module": tokenize(record.get("module", "")),
            "kdoc": tokenize((record.get("kdoc") or {}).get("body", "")),
            "params": tokenize(" ".join(param.get("name", "") + " " + param.get("type", "") for param in record.get("parameters", []))),
            "previews": tokenize(" ".join(render_preview(preview) for preview in record.get("previews", []))),
        }
        score = 0.0
        score += weighted_overlap(query_tokens, searchable["name"], 5.0)
        score += weighted_overlap(query_tokens, searchable["kdoc"], 4.0)
        score += weighted_overlap(query_tokens, searchable["params"], 2.5)
        score += weighted_overlap(query_tokens, searchable["previews"], 2.0)
        score += weighted_overlap(query_tokens, searchable["package"], 1.0)
        score += weighted_overlap(query_tokens, searchable["module"], 1.0)
        ranked.append((score, record))
    return sorted(ranked, key=lambda item: (-item[0], item[1].get("module", ""), item[1].get("name", "")))


def render_index_context(records: list[dict]) -> str:
    lines = []
    for record in records:
        params = ", ".join(
            f"{param.get('name')}: {param.get('type')}{' = ...' if param.get('hasDefault') else ''}"
            for param in record.get("parameters", [])
        )
        previews = ", ".join(render_preview(preview) for preview in record.get("previews", []))
        kdoc = (record.get("kdoc") or {}).get("summary", "")
        source = record.get("source", {})
        lines.append(
            "\n".join(
                [
                    f"id: {record.get('id')}",
                    f"name: {record.get('name')}",
                    f"module: {record.get('module')}",
                    f"source: {source.get('file')}:{source.get('line')}",
                    f"kdoc: {kdoc}",
                    f"parameters: {params}",
                    f"previews: {previews}",
                ]
            )
        )
    return "\n\n".join(lines)


def grep_source_files(prompt: str, source_by_file: dict[Path, str]) -> dict[Path, str]:
    tokens = tokenize(prompt)
    matches = {}
    for path, text in source_by_file.items():
        haystack = set(tokenize(text))
        if haystack.intersection(tokens):
            matches[path] = text
    return matches


def render_source_context(source_by_file: dict[Path, str]) -> str:
    chunks = []
    for path, text in source_by_file.items():
        chunks.append(f"// FILE: {path}\n{text}")
    return "\n\n".join(chunks)


def composable_names_in_context(context: str, expected: set[str]) -> list[str]:
    return [name for name in expected if re.search(rf"\b{name}\b", context)]


def contains_expected(names: list[str], expected: set[str]) -> bool:
    return bool(expected.intersection(names))


def first_rank(names: list[str], expected: set[str]) -> Optional[int]:
    for index, name in enumerate(names, start=1):
        if name in expected:
            return index
    return None


def tokenize(value: str) -> set[str]:
    words = re.findall(r"[a-z0-9]+", value.lower())
    return {word for word in words if len(word) > 1 and word not in STOPWORDS}


def split_camel(value: str) -> set[str]:
    spaced = re.sub(r"([a-z0-9])([A-Z])", r"\1 \2", value)
    return tokenize(f"{value} {spaced}")


def weighted_overlap(query_tokens: set[str], candidate_tokens: set[str], weight: float) -> float:
    return len(query_tokens.intersection(candidate_tokens)) * weight


def render_preview(preview: dict) -> str:
    args = preview.get("arguments", {})
    return " ".join(
        str(part)
        for part in [
            preview.get("annotation"),
            preview.get("name"),
            preview.get("group"),
            " ".join(f"{key} {value}" for key, value in args.items()),
        ]
        if part
    )


def count_tokens(text: str) -> int:
    # Deterministic fallback used in CI. Roughly matches common tokenizer ballparks for code/text.
    return math.ceil(len(text) / 4)


def print_report(results: list[BenchmarkResult]) -> None:
    by_method: dict[str, list[BenchmarkResult]] = {}
    for result in results:
        by_method.setdefault(result.method, []).append(result)

    print("Agent context benchmark")
    print("=======================")
    for method, method_results in by_method.items():
        hits = sum(1 for result in method_results if result.hit)
        total_tokens = sum(result.context_tokens for result in method_results)
        avg_tokens = total_tokens / len(method_results)
        ranks = [result.rank for result in method_results if result.rank is not None]
        mrr = sum(1 / rank for rank in ranks) / len(method_results) if ranks else 0.0
        print(f"{method}: hit_rate={hits}/{len(method_results)} avg_tokens={avg_tokens:.1f} total_tokens={total_tokens} mrr={mrr:.3f}")

    baseline = sum(result.context_tokens for result in by_method["full_source_dump"])
    compose = sum(result.context_tokens for result in by_method["compose_expose_top_k"])
    if compose:
        reduction = 100 * (1 - compose / baseline)
        print(f"ComposeExpose token reduction vs full source dump: {reduction:.1f}%")

    print("\nPer task")
    for result in results:
        print(
            f"{result.task_id:22} {result.method:20} "
            f"hit={str(result.hit):5} tokens={result.context_tokens:5} rank={result.rank or '-'}"
        )


if __name__ == "__main__":
    main()
