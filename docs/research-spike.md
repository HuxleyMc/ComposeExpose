# ComposeExpose Research Spike Notes

## What the spike proves

- A Gradle task can generate a stable JSON index for Compose declarations.
- The index captures composable name, package, visibility, module, source set, source location, KDoc, parameters, annotations, and direct or multipreview preview metadata.
- The MCP service can search, fetch by stable id, list previews, report stale indexes, and run an explicit Gradle refresh.
- The KSP processor can walk `@Composable` function symbols and emit the same schema for a future deeper integration.

## Freshness model

Agents should treat the generated index as a fast cache.

- Normal reads use `composables.json`.
- `index_status()` compares Kotlin source mtimes against `generatedAtEpochMillis`.
- If stale, call `refresh_index(module)` to run `./gradlew <module>:composeExposeIndex`.
- The spike does not run Gradle on every query.

## Known limitations

- The default source extractor is declaration-oriented and intentionally shallow.
- Default parameter detection is supported, but default values are not stored.
- The source extractor records preview annotation arguments as strings.
- Multi-module aggregation is implemented through `composeExposeAggregateIndex`.
- KSP output currently writes to KSP generated output via `createNewFileByPath`; production integration should decide whether to copy or merge that output into `build/composeExpose/composables.json`.

## Next implementation step

Promote the KSP processor from skeleton to the default extractor path:

1. Have the Gradle plugin detect/apply KSP when appropriate.
2. Pass module/source-root options to KSP.
3. Copy or merge KSP output into the canonical `build/composeExpose/composables.json`.
4. Add publishing, versioning, and real Android fixture coverage before external release.
