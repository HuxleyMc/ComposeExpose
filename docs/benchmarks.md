# Benchmarks

Benchmarks are intended as quick local smoke numbers, not lab-grade performance claims.

## Demo Indexing

Environment:

- Date: 2026-06-24
- Machine: local macOS development machine
- Command: `./scripts/benchmark-demo.sh 3`
- Workload: standalone `demo/` Android Compose app with 3 modules and 9 indexed composables
- Task: `composeExposeAggregateIndex`

Results:

| Run | real | user | sys |
| --- | ---: | ---: | ---: |
| 1 | 4.80s | 13.97s | 1.16s |
| 2 | 5.11s | 14.90s | 1.17s |
| 3 | 5.38s | 13.96s | 1.27s |

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

Result on 2026-06-24: `BUILD SUCCESSFUL in 1m 9s`.
