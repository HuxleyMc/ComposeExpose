# ComposeExpose Research Spike Notes

## What the spike proves

- A Gradle task can generate a stable JSON index for Compose declarations.
- The index captures composable name, package, visibility, module, source set, source location, KDoc, parameters, annotations, and direct or multipreview preview metadata.
- The MCP service can search, fetch by stable id, list previews, report stale indexes, and run an explicit Gradle refresh.
- The KSP processor can walk `@Composable` function symbols and emit the same schema used by the canonical index task.

## Freshness model

Agents should treat the generated index as a fast cache.

- Normal reads use `composables.json`.
- `index_status()` reports index age and compares Kotlin source mtimes against `generatedAtEpochMillis`.
- If stale, call `refresh_index()`. With a module argument, the MCP first runs `./gradlew <module>:composeExposeIndex` and then refreshes the aggregate index it serves.
- The spike does not run Gradle on every query.

## Known limitations

- The default source extractor is declaration-oriented and intentionally shallow.
- Default parameter detection is supported, but default values are not stored.
- The source extractor records preview annotation arguments as strings.
- Multi-module aggregation is implemented through `composeExposeAggregateIndex`.
- KSP output is copied/merged into `build/composeExpose/composables.json` when `composeExpose.backend` is set to `ksp`.

## Next implementation step

Prepare the KSP path for external release:

1. Decide whether KSP should become the default backend after compatibility testing.
2. Expand Android variant coverage beyond the demo's free/paid flavor smoke test before external release.
3. Add live-agent evals on top of the deterministic context benchmark.
4. Run the manual Maven Central release workflow against a real version and review the Central Portal validation output.
