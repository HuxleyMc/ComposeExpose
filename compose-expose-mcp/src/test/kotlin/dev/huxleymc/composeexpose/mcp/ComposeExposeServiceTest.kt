package dev.huxleymc.composeexpose.mcp

import dev.huxleymc.composeexpose.core.ComposableDeclaration
import dev.huxleymc.composeexpose.core.ComposableIndex
import dev.huxleymc.composeexpose.core.ComposableIndexJson
import dev.huxleymc.composeexpose.core.ComposableParameter
import dev.huxleymc.composeexpose.core.IndexMetadata
import dev.huxleymc.composeexpose.core.PreviewDeclaration
import dev.huxleymc.composeexpose.core.SourceLocation
import kotlin.io.path.createDirectories
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ComposeExposeServiceTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `search get and list previews read from index deterministically`() = runTest {
        val indexFile = writeIndex(sampleIndex())
        val service = ComposeExposeService(projectRoot = tempDir, indexFile = indexFile)

        assertEquals(listOf("AccountCard"), service.searchComposables(query = "account").map { it.name })
        assertEquals("AccountCard", service.getComposable(":app:main:dev.example.AccountCard#title:String")?.name)
        assertEquals(listOf("AccountCard"), service.listPreviews(group = "device").map { it.composableName })
    }

    @Test
    fun `status reports stale source without running gradle`() = runTest {
        val sourceRoot = tempDir.resolve("app/src/main/kotlin").createDirectories()
        val sourceFile = sourceRoot.resolve("Cards.kt")
        sourceFile.writeText("@Composable fun AccountCard() {}")
        val indexFile = writeIndex(
            sampleIndex(
                metadata = IndexMetadata(
                    generatedAtEpochMillis = sourceFile.getLastModifiedTime().toMillis() - 1_000,
                    projectRoot = tempDir.toString(),
                    modules = listOf(":app"),
                    sourceRoots = listOf(sourceRoot.toString()),
                ),
            ),
        )
        val service = ComposeExposeService(projectRoot = tempDir, indexFile = indexFile)

        val status = service.indexStatus()

        assertTrue(status.isStale)
        assertEquals(listOf(":app"), status.modules)
        assertFalse(status.refreshInProgress)
    }

    @Test
    fun `refresh invokes gradle task and reloads index`() = runTest {
        val indexFile = writeIndex(sampleIndex())
        var invoked = emptyList<String>()
        val service = ComposeExposeService(
            projectRoot = tempDir,
            indexFile = indexFile,
            gradleRunner = { args ->
                invoked = args
                writeIndex(sampleIndex(extraName = "FreshCard"))
                RefreshExecution(exitCode = 0, output = "indexed")
            },
        )

        val result = service.refreshIndex(module = ":app")

        assertEquals(listOf("./gradlew", ":app:composeExposeIndex"), invoked)
        assertTrue(result.success)
        assertFalse(result.status.refreshInProgress)
        assertNotNull(service.getComposable(":app:main:dev.example.FreshCard#"))
    }

    @Test
    fun `refresh returns structured failure and resets in-progress flag when gradle runner throws`() = runTest {
        val indexFile = writeIndex(sampleIndex())
        val service = ComposeExposeService(
            projectRoot = tempDir,
            indexFile = indexFile,
            gradleRunner = {
                throw IllegalStateException("gradle unavailable")
            },
        )

        val result = service.refreshIndex()

        assertFalse(result.success)
        assertTrue(result.output.contains("gradle unavailable"))
        assertFalse(result.status.refreshInProgress)
        assertFalse(service.indexStatus().refreshInProgress)
    }

    @Test
    fun `refresh rejects invalid module paths before invoking gradle`() = runTest {
        val indexFile = writeIndex(sampleIndex())
        var invoked = false
        val service = ComposeExposeService(
            projectRoot = tempDir,
            indexFile = indexFile,
            gradleRunner = {
                invoked = true
                RefreshExecution(exitCode = 0, output = "should not run")
            },
        )

        val result = service.refreshIndex(module = "../app")

        assertFalse(result.success)
        assertFalse(invoked)
        assertTrue(result.output.contains("Invalid Gradle module path"))
    }

    private fun writeIndex(index: ComposableIndex): Path {
        val indexFile = tempDir.resolve("build/composeExpose/composables.json")
        indexFile.parent.createDirectories()
        indexFile.writeText(ComposableIndexJson.encode(index))
        return indexFile
    }

    private fun sampleIndex(
        metadata: IndexMetadata = IndexMetadata(1234L, tempDir.toString(), listOf(":app"), emptyList()),
        extraName: String? = null,
    ): ComposableIndex {
        val composables = buildList {
            add(
                ComposableDeclaration(
                    id = ":app:main:dev.example.AccountCard#title:String",
                    module = ":app",
                    sourceSet = "main",
                    packageName = "dev.example",
                    name = "AccountCard",
                    visibility = "public",
                    source = SourceLocation("app/src/main/kotlin/dev/example/Cards.kt", 8, 1),
                    kdoc = null,
                    parameters = listOf(ComposableParameter("title", "String", hasDefault = false)),
                    annotations = listOf("@Composable", "@Preview"),
                    previews = listOf(PreviewDeclaration("Preview", "Phone", "device", mapOf("widthDp" to "390"))),
                ),
            )
            if (extraName != null) {
                add(
                    ComposableDeclaration(
                        id = ":app:main:dev.example.$extraName#",
                        module = ":app",
                        sourceSet = "main",
                        packageName = "dev.example",
                        name = extraName,
                        visibility = "public",
                        source = SourceLocation("app/src/main/kotlin/dev/example/Fresh.kt", 4, 1),
                        kdoc = null,
                        parameters = emptyList(),
                        annotations = listOf("@Composable"),
                        previews = emptyList(),
                    ),
                )
            }
        }
        return ComposableIndex(metadata, composables)
    }
}
