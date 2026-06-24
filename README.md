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

- `search_composables(query, module, sourceSet)`
- `get_composable(id)`
- `list_previews(group)`
- `refresh_index(module)`
- `index_status()`

Resources:

- `compose-expose://index`
- `compose-expose://modules`

## Demo app

The repo includes a standalone Android Compose demo app at `demo/`. It consumes the plugin through `includeBuild("..")`, so it works from a fresh clone without publishing ComposeExpose first.

Run a full demo smoke test:

```bash
./scripts/smoke-demo.sh
```

That command indexes the demo app, verifies the aggregate index has composables, and checks the MCP CLI help path.

Build the demo APK:

```bash
cd demo
../gradlew --no-daemon assembleDebug
```

Run a small indexing benchmark:

```bash
./scripts/benchmark-demo.sh 5
```

The demo aggregate index is written to:

```text
demo/build/composeExpose/all-composables.json
```

Point the MCP server at the demo:

```bash
./gradlew :compose-expose-mcp:run --args="--project-root $PWD/demo --index-file $PWD/demo/build/composeExpose/all-composables.json"
```

## Production status

The current implementation is usable, but the extraction backend is still evolving.

- The Gradle task uses a declaration-level source extractor so it can run in plain Gradle plugin tests.
- The KSP module validates the symbol-processing path and shared schema, but is not yet wired as the default extraction backend.
- `refresh_index(module)` validates Gradle module paths before invoking Gradle.
- `index_status()` lets agents check freshness before deciding to refresh.
- Function-body call relationships are out of scope for this spike; they likely need Kotlin PSI/Analysis API or compiler integration.
- Preview screenshots/rendering are out of scope.
