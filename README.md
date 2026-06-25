# ComposeExpose

ComposeExpose indexes Jetpack Compose UI declarations and serves them through MCP so agents can discover reusable composables without repeated grep passes.

It contains:

- `io.github.huxleymc.composeexpose`, a Gradle plugin that writes per-module and aggregate indexes.
- A declaration-level Compose extractor for names, bounded generic and receiver-qualified extension composables, source locations, KDoc, parameters, annotations, and previews.
- A KSP processor path that can produce the same index shape from `@Composable` symbols.
- A JVM MCP server command with stdio and Streamable HTTP transports.

## Gradle usage

Apply the plugin to each Kotlin or Android module you want indexed. Apply it to the root project too if you want an aggregate index for the whole build:

```kotlin
plugins {
    id("io.github.huxleymc.composeexpose")
}
```

The default backend is the source extractor. For compiler-backed declaration metadata, apply KSP and opt in:

```kotlin
plugins {
    id("com.google.devtools.ksp")
    id("io.github.huxleymc.composeexpose")
}

composeExpose {
    backend.set("ksp")
}

dependencies {
    ksp("io.github.huxleymc.composeexpose:compose-expose-ksp:<version>")
}
```

Run:

```bash
./gradlew composeExposeIndex
```

Per-module indexes are written to:

```text
build/composeExpose/composables.json
```

Run the aggregate task from the root project:

```bash
./gradlew composeExposeAggregateIndex
```

The aggregate index is written to:

```text
build/composeExpose/all-composables.json
```

## MCP usage

The server defaults to stdio, which is the right transport for local MCP clients:

```bash
./gradlew :compose-expose-mcp:run --args="--project-root /path/to/project"
```

Use an explicit index file when needed:

```bash
./gradlew :compose-expose-mcp:run --args="--project-root /path/to/project --index-file /path/to/project/build/composeExpose/all-composables.json"
```

Relative `--index-file` values resolve from `--project-root`.

Example MCP client config:

```json
{
  "mcpServers": {
    "compose-expose": {
      "command": "/path/to/project/gradlew",
      "args": [
        ":compose-expose-mcp:run",
        "--args=--project-root /path/to/android/project"
      ]
    }
  }
}
```

Streamable HTTP is available for inspector/debugging workflows:

```bash
./gradlew :compose-expose-mcp:run --args="--project-root /path/to/project --transport http --port 3000"
```

HTTP ports must be in the TCP range `1..65535`.

Connect an MCP inspector/client to:

```text
http://127.0.0.1:3000/mcp
```

## MCP surface

Tools:

- `search_composables(query, module, sourceSet, visibility, hasPreview, limit)`
- `get_composable(id)`
- `list_previews(group, module, sourceSet, annotation, limit)`
- `refresh_index(module)`
- `index_status()`

`search_composables` ranks exact name matches first, then prefix, substring, package, KDoc, parameter, annotation, and preview metadata matches. Results can be filtered by module, source set, Kotlin visibility, and preview presence. Results default to 20 items and are capped at 100 to keep agent context bounded.

`list_previews` can be filtered by preview group, Gradle module, source set, or preview annotation, which lets clients inspect reusable preview coverage without reading the full index. Preview results default to 20 items and are capped at 100.

Tool responses keep JSON text content for compatibility and also include MCP `structuredContent` wrappers for clients that can consume typed results directly: `results`, `composable`, `previews`, `result`, and `status`.

`tools/list` includes input property metadata and field-level output schemas for those structured wrappers, including composable records, preview rows, freshness status, and refresh results, so clients can plan calls without sampling each tool first.

The server also publishes MCP instructions that tell clients to check `index_status`, call `refresh_index` only when needed, prefer bounded searches, and consume `structuredContent` when supported.

Malformed or out-of-range tool arguments are returned as MCP tool errors with stable validation messages, so clients can fix the request and continue using the same session.

`refresh_index` returns a structured success flag, Gradle output, and a fresh `index_status` snapshot. Without a module argument it runs `composeExposeAggregateIndex`. With a module argument it runs `<module>:composeExposeIndex` and then `composeExposeAggregateIndex`, so the aggregate index served by MCP is refreshed before clients query again. Invalid module paths, concurrent refresh requests, and Gradle launch failures are returned as `success: false` results so MCP clients can recover without treating the server session as failed.

The MCP can start before an index has been generated. In that state query tools return empty results, `compose-expose://modules` returns an empty summary, and `index_status()` reports `exists: false` and `isStale: true`; clients should call `refresh_index()` before retrying discovery. If the index file exists but cannot be decoded, query tools also return empty results and `index_status()` reports `exists: true`, `isStale: true`, and an `error` string so clients can recover by calling `refresh_index()`.

`index_status().ageMillis` reports the index age directly, and `index_status().newerSources` reports stale files relative to the project root when they live under the project. Source roots outside the project are reported as normalized absolute paths so clients do not need to interpret `../` entries. If source freshness cannot be checked, `index_status()` reports `isStale: true` with an `error` string instead of failing the MCP call.

Resources:

- `compose-expose://index`
- `compose-expose://modules`
- `compose-expose://module/{module}`

`compose-expose://index` returns the full generated JSON index. `compose-expose://modules` returns a compact summary with generated timestamp, project root, source roots, and per-module composable counts, preview counts, source sets, and packages. `compose-expose://module/{module}` returns one module summary, for example `compose-expose://module/:app`.

## Demo app

The repo includes a standalone Android Compose demo app at `demo/`. It consumes the plugin and KSP processor through `includeBuild("..")`, so it works from a fresh clone without publishing ComposeExpose first.

Run a full demo smoke test:

```bash
./scripts/smoke-demo.sh
```

That command indexes the demo app, verifies the aggregate index has composables, and runs the MCP stdio process smoke.

Run the MCP stdio server smoke directly:

```bash
./scripts/smoke-mcp-stdio.sh
```

That command starts the installed MCP server process, sends JSON-RPC over stdio, calls `search_composables`, and reads `compose-expose://modules`.

Build the demo APK:

```bash
cd demo
../gradlew --no-daemon assembleFreeDebug assemblePaidDebug
```

Run a small indexing benchmark:

```bash
./scripts/benchmark-demo.sh 5
```

Run the agent-context benchmark:

```bash
./scripts/benchmark-agent-context.py
```

That benchmark compares with-ComposeExpose structured context against without-ComposeExpose grep-style matching files and full source dumps for retrieval hit rate, rank, and estimated token usage. Benchmarks are intentionally local-only so they can be run with realistic agent workflows:

```bash
./scripts/verify-agent-benchmark.py
```

The demo aggregate index is written to:

```text
demo/build/composeExpose/all-composables.json
```

Point the MCP server at the demo:

```bash
./gradlew :compose-expose-mcp:run --args="--project-root $PWD/demo --index-file $PWD/demo/build/composeExpose/all-composables.json"
```

## Published consumer smoke

Use the published-consumer fixture to verify installability without `includeBuild`:

```bash
./scripts/smoke-published-consumer.sh
```

The script publishes all artifacts to `build/local-maven`, then runs `fixtures/published-consumer` against the versioned plugin marker and KSP processor coordinates.

Verify public Maven metadata before publishing externally:

```bash
./scripts/verify-publishing-metadata.sh
```

Public publishing metadata is configured for every Maven publication, including the Gradle plugin marker. Release coordinates use the Maven Central namespace-compatible group and plugin ID:

```text
io.github.huxleymc.composeexpose
```

## Maven Central release

The `Release` GitHub Actions workflow runs for `v*` tags. It derives the release version from the tag, builds a Maven Central bundle, uploads it as a workflow artifact, and uploads the bundle to the Central Portal Publisher API with `USER_MANAGED` publishing.

Required GitHub secrets:

- `CENTRAL_PORTAL_USERNAME`
- `CENTRAL_PORTAL_PASSWORD`
- `SIGNING_IN_MEMORY_KEY`
- `SIGNING_IN_MEMORY_KEY_PASSWORD`

Build a release bundle locally:

```bash
./scripts/build-central-bundle.sh 0.1.0
```

Upload an already-built bundle:

```bash
CENTRAL_PORTAL_USERNAME=... CENTRAL_PORTAL_PASSWORD=... \
  ./scripts/upload-central-bundle.sh build/central-portal/compose-expose-0.1.0-central-bundle.zip 0.1.0 USER_MANAGED
```

The configured Central namespace is `io.github.huxleymc`. The bundle upload defaults to `USER_MANAGED`, so a validated deployment can be reviewed and published in the Central Portal UI.

## Production status

The current implementation is usable, but still needs broader compatibility testing and release automation before external release.

- CI runs formatting, root build, and demo/MCP smoke on pull requests, `main` pushes, and version tags. Release smoke checks for publishing metadata and published-consumer installability run only for `v*` tags.
- Release automation builds and uploads a signed Central Portal bundle for the `io.github.huxleymc` namespace only on `v*` tag pushes.
- The Gradle task supports `source` and `ksp` backends.
- Artifacts can be published to a local Maven repository and consumed by a standalone fixture without `includeBuild`.
- Publications include Maven POM URL, license, developer, and SCM metadata, plus optional in-memory signing for non-snapshot remote publishing.
- The demo uses the KSP backend and verifies KDoc, arguments, previews, source location, aggregate indexing, and flavor-specific composables with duplicate names.
- KSP indexes Android source sets from file paths, so `src/free` and `src/paid` composables produce distinct stable IDs.
- `refresh_index(module)` validates Gradle module paths before invoking Gradle.
- `index_status()` lets agents check freshness and index age before deciding to refresh.
- Function-body call relationships are out of scope for this spike; they likely need Kotlin PSI/Analysis API or compiler integration.
- Preview screenshots/rendering are out of scope.
