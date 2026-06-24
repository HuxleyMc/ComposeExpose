# Benchmarks

The primary benchmark question is whether ComposeExpose gives agents better context with fewer tokens. Runtime numbers are secondary smoke checks.

Token counts use a deterministic estimate of `ceil(chars / 4)` so the benchmark runs without provider-specific tokenizer packages.

## Agent Context

Command:

```bash
./scripts/benchmark-agent-context.py
```

Workload:

- Demo Android Compose app
- 3 modules
- 9 indexed composables
- 6 agent-style lookup tasks in `evals/agent-context-tasks.json`
- ComposeExpose context uses top 3 structured composable records

Results from 2026-06-24:

| Method | Hit rate | MRR | Avg tokens/task | Total tokens |
| --- | ---: | ---: | ---: | ---: |
| ComposeExpose top-k | 6/6 | 1.000 | 320.0 | 1,920 |
| Grep matching files | 6/6 | n/a | 1,279.0 | 7,674 |
| Full source dump | 6/6 | n/a | 1,371.0 | 8,226 |

ComposeExpose reduced estimated context tokens by 76.7% versus full source dump while putting the expected composable at rank 1 for every task.

This is not a live LLM quality benchmark yet. It is a deterministic retrieval/context benchmark that measures whether the MCP can put the right reusable composable into a much smaller prompt budget.

## Demo Indexing

Environment:

- Date: 2026-06-24
- Machine: local macOS development machine
- Command: `./scripts/benchmark-demo.sh 3`
- Workload: standalone `demo/` Android Compose app with 3 modules and 9 indexed composables
- Task: `composeExposeAggregateIndex`
- Backend: KSP

Results:

| Run | real | user | sys |
| --- | ---: | ---: | ---: |
| 1 | 14.90s | 1.09s | 0.14s |
| 2 | 16.93s | 0.97s | 0.13s |
| 3 | 15.68s | 1.04s | 0.15s |

The final aggregate index contained 9 composables.

To reproduce:

```bash
./scripts/benchmark-demo.sh 3
```

## Demo Assemble

The demo app was also verified as a real Android build:

```bash
cd demo
../gradlew --no-daemon assembleDebug
```

Result on 2026-06-24 with the KSP backend enabled: `BUILD SUCCESSFUL in 36s`.

The assemble output was also checked to ensure AGP did not emit the duplicate packaged resource warning for `composeExpose/composables.json`.
