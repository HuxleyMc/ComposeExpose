package dev.huxleymc.composeexpose.gradle

import dev.huxleymc.composeexpose.core.ComposableIndexJson
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ComposeExposePluginFunctionalTest {
    @TempDir
    lateinit var projectDir: Path

    @Test
    fun `composeExposeIndex writes composable index for kotlin source roots`() {
        projectDir.resolve("settings.gradle.kts").writeText("""pluginManagement { repositories { google(); gradlePluginPortal(); mavenCentral() } }""")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("dev.huxleymc.composeexpose")
            }
            """.trimIndent(),
        )
        val source = projectDir.resolve("src/main/kotlin/dev/example/Button.kt")
        source.parent.createDirectories()
        source.writeText(
            """
            package dev.example

            import androidx.compose.runtime.Composable

            /** Saves the current form. */
            @Composable
            fun SaveButton(enabled: Boolean = true) {}
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments("composeExposeIndex", "--stacktrace")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":composeExposeIndex")?.outcome)
        val index = ComposableIndexJson.decode(projectDir.resolve("build/composeExpose/composables.json").readText())
        assertEquals(listOf("SaveButton"), index.composables.map { it.name })
        assertTrue(index.metadata.sourceRoots.single().endsWith("src/main/kotlin"))
    }

    @Test
    fun `root aggregate task merges indexed subprojects`() {
        projectDir.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement { repositories { google(); gradlePluginPortal(); mavenCentral() } }
            include(":app", ":design")
            """.trimIndent(),
        )
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("dev.huxleymc.composeexpose")
            }
            """.trimIndent(),
        )
        listOf("app" to "AppCard", "design" to "DesignButton").forEach { (module, name) ->
            projectDir.resolve("$module/build.gradle.kts").apply {
                parent.createDirectories()
                writeText("""plugins { id("dev.huxleymc.composeexpose") }""")
            }
            val source = projectDir.resolve("$module/src/main/kotlin/dev/example/$name.kt")
            source.parent.createDirectories()
            source.writeText(
                """
                package dev.example

                import androidx.compose.runtime.Composable

                @Composable
                fun $name() {}
                """.trimIndent(),
            )
        }

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments("composeExposeAggregateIndex", "--stacktrace")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":composeExposeAggregateIndex")?.outcome)
        val index = ComposableIndexJson.decode(projectDir.resolve("build/composeExpose/all-composables.json").readText())
        assertEquals(listOf(":app", ":design"), index.metadata.modules)
        assertEquals(listOf("AppCard", "DesignButton"), index.composables.map { it.name })
    }

    @Test
    fun `ksp backend fails clearly when ksp output is missing`() {
        projectDir.resolve("settings.gradle.kts").writeText("""pluginManagement { repositories { google(); gradlePluginPortal(); mavenCentral() } }""")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("dev.huxleymc.composeexpose")
            }

            composeExpose {
                backend.set("ksp")
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments("composeExposeIndex", "--stacktrace")
            .withPluginClasspath()
            .buildAndFail()

        assertTrue(result.output.contains("ComposeExpose KSP backend did not find generated index output"))
    }
}
