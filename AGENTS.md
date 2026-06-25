# Repository Guidelines

## Project Structure & Module Organization

ComposeExpose is a Gradle Kotlin DSL multi-module project. Root modules are:

- `compose-expose-core/`: shared index schema, source-set detection, JSON merge/extraction logic.
- `compose-expose-gradle-plugin/`: Gradle tasks and extension wiring for index generation.
- `compose-expose-ksp/`: KSP processor registration and symbol processing entry point.
- `compose-expose-mcp/`: MCP CLI/server, transports, service layer, and protocol tests.

Kotlin source is under each module's `src/main/kotlin`; tests are under `src/test/kotlin`. The standalone Android Compose sample is in `demo/` and uses `includeBuild("..")`. Fixtures are in `fixtures/`, scripts in `scripts/`, docs and benchmarks in `docs/`, and agent benchmark tasks in `evals/`.

## Build, Test, and Development Commands

- `./gradlew spotlessCheck`: run Spotless/ktlint formatting checks for root modules.
- `./gradlew spotlessApply`: apply ktlint formatting where possible.
- `./gradlew clean build`: compile all root modules and run JVM tests.
- `./gradlew :compose-expose-mcp:run --args="--project-root $PWD/demo"`: run the MCP server against the demo project.
- `./scripts/smoke-demo.sh`: build the demo index and run the MCP stdio smoke test.
- `./scripts/smoke-published-consumer.sh`: publish to `build/local-maven` and verify the fixture consumer.
- `./gradlew -p demo spotlessCheck`: check formatting for the standalone demo build.

## Coding Style & Naming Conventions

Use Kotlin with 4-space indentation and Gradle Kotlin DSL for build logic. Formatting is enforced by Spotless with ktlint `1.8.0`; run `spotlessApply` before broad edits. Keep packages under `dev.huxleymc.composeexpose.*`. Use PascalCase for classes and objects, camelCase for functions and properties, and descriptive Gradle task names such as `composeExposeAggregateIndex`. `.editorconfig` allows PascalCase functions annotated with `@Composable`.

## Testing Guidelines

Tests use `kotlin("test")` on JUnit Platform. Name test files after the subject, for example `SourceSetDetectorTest.kt` or `ComposeExposeServiceTest.kt`. Run `./gradlew test` for JVM tests, `./gradlew clean build` before larger changes, and the relevant smoke script when touching Gradle plugin, demo indexing, publishing, or MCP behavior.

## Commit & Pull Request Guidelines

Recent history uses short Conventional Commit subjects, especially `feat: ...`; follow that style with imperative, lower-case summaries such as `fix: refresh stale mcp index`. Pull requests should describe the change, list verification commands, link issues when available, and include screenshots or command snippets for demo, CLI, MCP, or publishing changes.

## Security & Configuration Tips

Do not commit signing keys, publishing credentials, or local MCP client config. Publishing reads Gradle properties and environment variables such as `COMPOSE_EXPOSE_PUBLISH_USERNAME`, `COMPOSE_EXPOSE_PUBLISH_PASSWORD`, `signingInMemoryKey`, and `signingInMemoryKeyPassword`.
