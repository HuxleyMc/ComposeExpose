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
- 20 indexed composables, including multipreview functions and free/paid flavor-specific composables with duplicate names
- 13 agent-style lookup tasks in `evals/agent-context-tasks.json`
- ComposeExpose context uses top 3 structured composable records

Results from 2026-06-24:

| Method | Hit rate | MRR | Avg tokens/task | Total tokens |
| --- | ---: | ---: | ---: | ---: |
| With ComposeExpose top-k | 13/13 | 0.962 | 376.6 | 4,896 |
| Without ComposeExpose grep files | 13/13 | n/a | 3,033.2 | 39,432 |
| Without ComposeExpose full source | 13/13 | n/a | 3,600.0 | 46,800 |

ComposeExpose reduced estimated context tokens by 87.6% versus the best non-ComposeExpose baseline and 89.5% versus full source dump. The expected composable was in the top 3 for every task and ranked first for 12 of 13 tasks.

This is not a live LLM quality benchmark yet. It is a deterministic retrieval/context benchmark that measures whether the MCP can put the right reusable composable into a much smaller prompt budget.

## Demo Indexing

Environment:

- Date: 2026-06-24
- Machine: local macOS development machine
- Command: `./scripts/benchmark-demo.sh 3`
- Workload: standalone `demo/` Android Compose app with 3 modules and 20 indexed composables
- Task: `composeExposeAggregateIndex`
- Backend: KSP

Results:

| Run | real | user | sys |
| --- | ---: | ---: | ---: |
| 1 | 14.71s | 0.92s | 0.11s |
| 2 | 12.59s | 0.89s | 0.11s |
| 3 | 12.32s | 0.85s | 0.11s |

The final aggregate index contained 20 composables.

To reproduce:

```bash
./scripts/benchmark-demo.sh 3
```

## Demo Assemble

The demo app was also verified as a real Android build:

```bash
cd demo
../gradlew --no-daemon assembleFreeDebug assemblePaidDebug
```

Result on 2026-06-24 with the KSP backend enabled: `BUILD SUCCESSFUL`.

The assemble output was also checked to ensure AGP did not emit the duplicate packaged resource warning for `composeExpose/composables.json`.

## Published Consumer

Command:

```bash
./scripts/smoke-published-consumer.sh
```

This publishes all ComposeExpose artifacts into `build/local-maven`, then verifies a separate fixture can resolve:

- `dev.huxleymc.composeexpose` Gradle plugin marker
- `dev.huxleymc.composeexpose:compose-expose-ksp:0.1.0-SNAPSHOT`
- plugin runtime dependencies

Result on 2026-06-24: `Published consumer indexed 2 composables: PublishedCard, PublishedCardPreview`.
