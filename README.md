# ComposeExpose

ComposeExpose indexes Jetpack Compose UI declarations and serves them through MCP so agents can discover reusable composables without repeated grep passes.

It contains:

- `dev.huxleymc.composeexpose`, a Gradle plugin that writes per-module and aggregate indexes.
- A declaration-level Compose extractor for names, source locations, KDoc, parameters, annotations, and previews.
- A KSP processor path that can produce the same index shape from `@Composable` symbols.
- A JVM MCP server command with stdio and Streamable HTTP transports.

## Gradle usage

Apply the plugin to each Kotlin or Android module you want indexed. Apply it to the root project too if you want an aggregate index for the whole build:

```kotlin
plugins {
    id("dev.huxleymc.composeexpose")
}
```

The default backend is the source extractor. For compiler-backed declaration metadata, apply KSP and opt in:

```kotlin
plugins {
    id("com.google.devtools.ksp")
    id("dev.huxleymc.composeexpose")
}

composeExpose {
    backend.set("ksp")
}

dependencies {
    ksp("dev.huxleymc.composeexpose:compose-expose-ksp:<version>")
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

Connect an MCP inspector/client to:

```text
http://127.0.0.1:3000/mcp
```

## MCP surface

Tools:

- `search_composables(query, module, sourceSet, limit)`
- `get_composable(id)`
- `list_previews(group)`
- `refresh_index(module)`
- `index_status()`

`search_composables` ranks exact name matches first, then prefix, substring, package, and KDoc matches. Results default to 20 items and are capped at 100 to keep agent context bounded.

`refresh_index` returns a structured success flag, Gradle output, and a fresh `index_status` snapshot. Invalid module paths and Gradle launch failures are returned as `success: false` results so MCP clients can recover without treating the server session as failed.

Resources:

- `compose-expose://index`
- `compose-expose://modules`

`compose-expose://index` returns the full generated JSON index. `compose-expose://modules` returns a compact summary with generated timestamp, project root, source roots, and per-module composable counts, preview counts, source sets, and packages.

## Demo app

The repo includes a standalone Android Compose demo app at `demo/`. It consumes the plugin and KSP processor through `includeBuild("..")`, so it works from a fresh clone without publishing ComposeExpose first.

Run a full demo smoke test:

```bash
./scripts/smoke-demo.sh
```

That command indexes the demo app, verifies the aggregate index has composables, and checks the MCP CLI help path.

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

That benchmark compares ComposeExpose structured context against grep-style matching files and full source dumps for retrieval hit rate, rank, and estimated token usage.

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

Public publishing metadata is configured for every Maven publication, including the Gradle plugin marker. Local snapshot publishing does not require signing keys. For remote publishing, provide:

- `composeExposePublishUrl`
- `composeExposePublishUsername` or `COMPOSE_EXPOSE_PUBLISH_USERNAME`
- `composeExposePublishPassword` or `COMPOSE_EXPOSE_PUBLISH_PASSWORD`
- `signingInMemoryKey`
- `signingInMemoryKeyPassword`

## Production status

The current implementation is usable, but still needs broader compatibility testing and release automation before external release.

- The Gradle task supports `source` and `ksp` backends.
- Artifacts can be published to a local Maven repository and consumed by a standalone fixture without `includeBuild`.
- Publications include Maven POM URL, license, developer, and SCM metadata, plus optional in-memory signing for non-snapshot remote publishing.
- The demo uses the KSP backend and verifies KDoc, arguments, previews, source location, aggregate indexing, and flavor-specific composables with duplicate names.
- KSP indexes Android source sets from file paths, so `src/free` and `src/paid` composables produce distinct stable IDs.
- `refresh_index(module)` validates Gradle module paths before invoking Gradle.
- `index_status()` lets agents check freshness before deciding to refresh.
- Function-body call relationships are out of scope for this spike; they likely need Kotlin PSI/Analysis API or compiler integration.
- Preview screenshots/rendering are out of scope.
