# Spotless Version Catalog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a shared Gradle version catalog and Spotless ktlint checks.

**Architecture:** Create the root catalog in `gradle/libs.versions.toml`, import it from the nested `demo` settings file, and replace inline plugin/dependency coordinates with catalog aliases. Apply and configure Spotless in the root and demo builds.

**Tech Stack:** Gradle Kotlin DSL, Gradle version catalogs, Spotless Gradle plugin, ktlint.

---

### Task 1: Add Shared Catalog And Spotless

**Files:**
- Create: `.editorconfig`
- Create: `demo/.editorconfig`
- Create: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts`
- Modify: `settings.gradle.kts`
- Modify: `demo/settings.gradle.kts`
- Modify: module `build.gradle.kts` files under root and `demo`

- [x] **Step 1: Verify missing Spotless task**

Run: `./gradlew spotlessCheck`
Expected: FAIL with `Task 'spotlessCheck' not found`.

- [x] **Step 2: Add catalog aliases**

Create `gradle/libs.versions.toml` with plugin aliases for Kotlin, Android, Compose, and Spotless plus library aliases for existing dependencies.

- [x] **Step 3: Replace inline coordinates**

Use `alias(libs.plugins...)` in plugin blocks and `libs...` dependency aliases in dependency blocks.

- [x] **Step 4: Import catalog into demo**

Configure `demo/settings.gradle.kts` to load `../gradle/libs.versions.toml`.

- [x] **Step 5: Configure Spotless**

Apply Spotless to relevant root and demo projects, configure `kotlin` and `kotlinGradle` targets, pin ktlint through `libs.versions.ktlint`, and exclude generated/build outputs.

- [x] **Step 6: Configure Compose naming**

Add `.editorconfig` and `demo/.editorconfig` with `ktlint_function_naming_ignore_when_annotated_with = Composable`.

- [x] **Step 7: Verify root build**

Run: `./gradlew spotlessCheck`
Expected: PASS.

- [x] **Step 8: Verify demo build**

Run: `./gradlew -p demo spotlessCheck`
Expected: PASS.

- [x] **Step 9: Verify normal builds**

Run: `./gradlew check` and `./gradlew -p demo :app:assembleDebug`
Expected: PASS.
