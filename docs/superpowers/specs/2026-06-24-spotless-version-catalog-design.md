# Spotless Version Catalog Design

## Goal

Centralize Gradle plugin and library versions in a shared version catalog, then add Spotless with ktlint formatting checks for Kotlin source and Gradle Kotlin scripts.

## Architecture

The root build owns `gradle/libs.versions.toml`. The nested `demo` build imports that same catalog from `../gradle/libs.versions.toml`, so both builds resolve the same plugin and dependency aliases while remaining independently runnable Gradle builds.

Spotless is applied to each project that should expose formatting tasks. Root and demo build scripts are checked, root subprojects format Kotlin source and Gradle Kotlin scripts, and demo projects format their own Gradle Kotlin scripts and Kotlin source when present. Generated and build output are excluded.

A repo-level `.editorconfig` keeps ktlint compatible with Compose naming by allowing PascalCase functions annotated with `@Composable`. The standalone `demo` build carries the same minimal `.editorconfig` because its ktlint execution resolves configuration from the demo project boundary.

## Testing

Use Gradle as the behavioral check:

- Before implementation, `./gradlew spotlessCheck` fails because no Spotless task exists.
- After implementation, `./gradlew spotlessCheck` should resolve and run on the root build.
- After implementation, `../gradlew -p demo spotlessCheck` should resolve and run on the demo build.
