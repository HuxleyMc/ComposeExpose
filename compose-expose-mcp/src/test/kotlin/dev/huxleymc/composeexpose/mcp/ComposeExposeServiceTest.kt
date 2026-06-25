package dev.huxleymc.composeexpose.mcp

import dev.huxleymc.composeexpose.core.ComposableDeclaration
import dev.huxleymc.composeexpose.core.ComposableIndex
import dev.huxleymc.composeexpose.core.ComposableIndexJson
import dev.huxleymc.composeexpose.core.ComposableParameter
import dev.huxleymc.composeexpose.core.IndexMetadata
import dev.huxleymc.composeexpose.core.Kdoc
import dev.huxleymc.composeexpose.core.PreviewDeclaration
import dev.huxleymc.composeexpose.core.SourceLocation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ComposeExposeServiceTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `search get and list previews read from index deterministically`() =
        runTest {
            val indexFile = writeIndex(sampleIndex())
            val service = ComposeExposeService(projectRoot = tempDir, indexFile = indexFile)

            assertEquals(listOf("AccountCard"), service.searchComposables(query = "account").map { it.name })
            assertEquals("AccountCard", service.getComposable(":app:main:dev.example.AccountCard#title:String")?.name)
            assertEquals(listOf("AccountCard"), service.listPreviews(group = "device").map { it.composableName })
        }

    @Test
    fun `search ranks exact name matches first and limits results`() =
        runTest {
            val indexFile =
                writeIndex(
                    sampleIndex(
                        extraDeclarations =
                            listOf(
                                sampleComposable(
                                    name = "AccountCardPreviewHost",
                                    source = "app/src/main/kotlin/dev/example/AccountPreview.kt",
                                ),
                                sampleComposable(
                                    name = "BillingPanel",
                                    source = "app/src/main/kotlin/dev/account/BillingPanel.kt",
                                    packageName = "dev.account",
                                ),
                                sampleComposable(
                                    name = "BalanceTile",
                                    source = "app/src/main/kotlin/dev/example/BalanceTile.kt",
                                    kdocBody = "Uses the reusable account visual treatment.",
                                ),
                            ),
                    ),
                )
            val service = ComposeExposeService(projectRoot = tempDir, indexFile = indexFile)

            val results = service.searchComposables(query = "AccountCard", limit = 2)

            assertEquals(listOf("AccountCard", "AccountCardPreviewHost"), results.map { it.name })
        }

    @Test
    fun `search matches parameters annotations and preview metadata`() =
        runTest {
            val indexFile =
                writeIndex(
                    sampleIndex(
                        extraDeclarations =
                            listOf(
                                sampleComposable(
                                    name = "ActionPanel",
                                    source = "app/src/main/kotlin/dev/example/ActionPanel.kt",
                                    parameters =
                                        listOf(
                                            ComposableParameter("onRetry", "() -> Unit", hasDefault = false),
                                            ComposableParameter("state", "PanelState", hasDefault = true),
                                        ),
                                    previews =
                                        listOf(
                                            PreviewDeclaration(
                                                annotation = "TabletPreviews",
                                                name = "Retry",
                                                group = "workflow",
                                                arguments = mapOf("widthDp" to "840"),
                                            ),
                                        ),
                                    annotations = listOf("@Composable", "@TabletPreviews"),
                                ),
                            ),
                    ),
                )
            val service = ComposeExposeService(projectRoot = tempDir, indexFile = indexFile)

            assertEquals(listOf("ActionPanel"), service.searchComposables(query = "onRetry").map { it.name })
            assertEquals(listOf("ActionPanel"), service.searchComposables(query = "PanelState").map { it.name })
            assertEquals(listOf("ActionPanel"), service.searchComposables(query = "workflow").map { it.name })
            assertEquals(listOf("ActionPanel"), service.searchComposables(query = "TabletPreviews").map { it.name })
        }

    @Test
    fun `module summaries include counts packages previews and source sets`() =
        runTest {
            val indexFile =
                writeIndex(
                    sampleIndex(
                        metadata =
                            IndexMetadata(
                                generatedAtEpochMillis = 1234L,
                                projectRoot = tempDir.toString(),
                                modules = listOf(":app", ":design"),
                                sourceRoots =
                                    listOf(
                                        tempDir.resolve("app/src/main/kotlin").toString(),
                                        tempDir.resolve("design/src/main/kotlin").toString(),
                                    ),
                            ),
                        extraDeclarations =
                            listOf(
                                sampleComposable(
                                    name = "VariantBadge",
                                    source = "app/src/free/kotlin/dev/example/VariantBadge.kt",
                                    sourceSet = "free",
                                ),
                                sampleComposable(
                                    name = "ThemeWrapper",
                                    source = "design/src/main/kotlin/dev/design/ThemeWrapper.kt",
                                    module = ":design",
                                    packageName = "dev.design",
                                ),
                            ),
                    ),
                )
            val service = ComposeExposeService(projectRoot = tempDir, indexFile = indexFile)

            val summaries = service.moduleSummaries()

            assertEquals(1234L, summaries.generatedAtEpochMillis)
            assertEquals(2, summaries.modules.size)
            val app = summaries.modules.single { it.module == ":app" }
            assertEquals(2, app.composableCount)
            assertEquals(1, app.previewCount)
            assertEquals(listOf("free", "main"), app.sourceSets)
            assertEquals(listOf("dev.example"), app.packages)
            val design = summaries.modules.single { it.module == ":design" }
            assertEquals(1, design.composableCount)
            assertEquals(listOf("dev.design"), design.packages)
        }

    @Test
    fun `queries return empty results when index has not been generated yet`() =
        runTest {
            val indexFile = tempDir.resolve("build/composeExpose/all-composables.json")
            val service = ComposeExposeService(projectRoot = tempDir, indexFile = indexFile)

            assertEquals(emptyList(), service.searchComposables(query = "AccountCard"))
            assertEquals(null, service.getComposable(":app:main:dev.example.AccountCard#"))
            assertEquals(emptyList(), service.listPreviews())

            val summaries = service.moduleSummaries()
            assertEquals(0L, summaries.generatedAtEpochMillis)
            assertEquals(tempDir.toString(), summaries.projectRoot)
            assertEquals(emptyList(), summaries.sourceRoots)
            assertEquals(emptyList(), summaries.modules)

            val status = service.indexStatus()
            assertFalse(status.exists)
            assertTrue(status.isStale)
        }

    @Test
    fun `queries and status return recoverable error when index json is corrupt`() =
        runTest {
            val indexFile = tempDir.resolve("build/composeExpose/composables.json")
            indexFile.parent.createDirectories()
            indexFile.writeText("{not-json")
            val service = ComposeExposeService(projectRoot = tempDir, indexFile = indexFile)

            assertEquals(emptyList(), service.searchComposables(query = "AccountCard"))
            assertEquals(null, service.getComposable(":app:main:dev.example.AccountCard#"))
            assertEquals(emptyList(), service.listPreviews())

            val summaries = service.moduleSummaries()
            assertEquals(0L, summaries.generatedAtEpochMillis)
            assertEquals(emptyList(), summaries.modules)

            val status = service.indexStatus()
            assertTrue(status.exists)
            assertTrue(status.isStale)
            assertNotNull(status.error)
            assertTrue(status.error.contains("Failed to read ComposeExpose index"))
        }

    @Test
    fun `status reports stale source without running gradle`() =
        runTest {
            val sourceRoot = tempDir.resolve("app/src/main/kotlin").createDirectories()
            val sourceFile = sourceRoot.resolve("Cards.kt")
            sourceFile.writeText("@Composable fun AccountCard() {}")
            val indexFile =
                writeIndex(
                    sampleIndex(
                        metadata =
                            IndexMetadata(
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
    fun `status reports index age in milliseconds`() =
        runTest {
            val indexFile =
                writeIndex(
                    sampleIndex(
                        metadata =
                            IndexMetadata(
                                generatedAtEpochMillis = 1_000L,
                                projectRoot = tempDir.toString(),
                                modules = listOf(":app"),
                                sourceRoots = emptyList(),
                            ),
                    ),
                )
            val service =
                ComposeExposeService(
                    projectRoot = tempDir,
                    indexFile = indexFile,
                    currentTimeMillis = { 2_500L },
                )

            val status = service.indexStatus()

            assertEquals(1_500L, status.ageMillis)
        }

    @Test
    fun `status reports stale external source roots without crashing`() =
        runTest {
            val projectRoot = tempDir.resolve("project").createDirectories()
            val externalSourceRoot = tempDir.resolve("included-build/src/main/kotlin").createDirectories()
            val sourceFile = externalSourceRoot.resolve("ExternalCard.kt")
            sourceFile.writeText("@Composable fun ExternalCard() {}")
            val indexFile =
                writeIndex(
                    sampleIndex(
                        metadata =
                            IndexMetadata(
                                generatedAtEpochMillis = sourceFile.getLastModifiedTime().toMillis() - 1_000,
                                projectRoot = projectRoot.toString(),
                                modules = listOf(":included"),
                                sourceRoots = listOf(externalSourceRoot.toString()),
                            ),
                    ),
                    projectRoot,
                )
            val service = ComposeExposeService(projectRoot = projectRoot, indexFile = indexFile)

            val status = service.indexStatus()

            assertTrue(status.isStale)
            assertEquals(listOf(sourceFile.toString()), status.newerSources)
            assertEquals(null, status.error)
        }

    @Test
    fun `status resolves relative source roots from project root`() =
        runTest {
            val projectRoot = tempDir.resolve("project").createDirectories()
            val sourceRoot = projectRoot.resolve("app/src/main/kotlin").createDirectories()
            val sourceFile = sourceRoot.resolve("Cards.kt")
            sourceFile.writeText("@Composable fun AccountCard() {}")
            val indexFile =
                writeIndex(
                    sampleIndex(
                        metadata =
                            IndexMetadata(
                                generatedAtEpochMillis = sourceFile.getLastModifiedTime().toMillis() - 1_000,
                                projectRoot = projectRoot.toString(),
                                modules = listOf(":app"),
                                sourceRoots = listOf("app/src/main/kotlin"),
                            ),
                    ),
                    projectRoot,
                )
            val service = ComposeExposeService(projectRoot = projectRoot, indexFile = indexFile)

            val status = service.indexStatus()

            assertTrue(status.isStale)
            assertEquals(listOf("app/src/main/kotlin/Cards.kt"), status.newerSources)
            assertEquals(null, status.error)
        }

    @Test
    fun `refresh invokes aggregate gradle task and reloads index`() =
        runTest {
            val indexFile = writeIndex(sampleIndex())
            var invoked = emptyList<String>()
            val service =
                ComposeExposeService(
                    projectRoot = tempDir,
                    indexFile = indexFile,
                    gradleRunner = { args ->
                        invoked = args
                        writeIndex(sampleIndex(extraName = "FreshCard"))
                        RefreshExecution(exitCode = 0, output = "indexed")
                    },
                )

            val result = service.refreshIndex()

            assertEquals(listOf("./gradlew", "composeExposeAggregateIndex"), invoked)
            assertTrue(result.success)
            assertFalse(result.status.refreshInProgress)
            assertNotNull(service.getComposable(":app:main:dev.example.FreshCard#"))
        }

    @Test
    fun `module refresh runs module task then aggregate task before reloading served index`() =
        runTest {
            val indexFile = writeIndex(sampleIndex())
            val invocations = mutableListOf<List<String>>()
            val service =
                ComposeExposeService(
                    projectRoot = tempDir,
                    indexFile = indexFile,
                    gradleRunner = { args ->
                        invocations += args
                        if (args == listOf("./gradlew", "composeExposeAggregateIndex")) {
                            writeIndex(sampleIndex(extraName = "FreshCard"))
                        }
                        RefreshExecution(exitCode = 0, output = "indexed ${args.last()}")
                    },
                )

            val result = service.refreshIndex(module = ":app")

            assertEquals(
                listOf(
                    listOf("./gradlew", ":app:composeExposeIndex"),
                    listOf("./gradlew", "composeExposeAggregateIndex"),
                ),
                invocations,
            )
            assertTrue(result.success)
            assertTrue(result.output.contains(":app:composeExposeIndex"))
            assertTrue(result.output.contains("composeExposeAggregateIndex"))
            assertNotNull(service.getComposable(":app:main:dev.example.FreshCard#"))
        }

    @Test
    fun `refresh returns structured failure and resets in-progress flag when gradle runner throws`() =
        runTest {
            val indexFile = writeIndex(sampleIndex())
            val service =
                ComposeExposeService(
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
    fun `refresh rejects invalid module paths before invoking gradle`() =
        runTest {
            val indexFile = writeIndex(sampleIndex())
            var invoked = false
            val service =
                ComposeExposeService(
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

    @Test
    fun `refresh rejects concurrent requests without launching another gradle process`() =
        runTest {
            val indexFile = writeIndex(sampleIndex())
            val firstRefreshStarted = CompletableDeferred<Unit>()
            val releaseFirstRefresh = CompletableDeferred<Unit>()
            var invocationCount = 0
            val service =
                ComposeExposeService(
                    projectRoot = tempDir,
                    indexFile = indexFile,
                    gradleRunner = {
                        invocationCount += 1
                        firstRefreshStarted.complete(Unit)
                        releaseFirstRefresh.await()
                        RefreshExecution(exitCode = 0, output = "indexed")
                    },
                )

            val firstRefresh = async { service.refreshIndex() }
            firstRefreshStarted.await()

            val secondRefresh = service.refreshIndex()

            assertFalse(secondRefresh.success)
            assertTrue(secondRefresh.output.contains("already in progress"))
            assertTrue(secondRefresh.status.refreshInProgress)
            assertEquals(1, invocationCount)

            releaseFirstRefresh.complete(Unit)
            assertTrue(firstRefresh.await().success)
            assertFalse(service.indexStatus().refreshInProgress)
            assertEquals(1, invocationCount)
        }

    private fun writeIndex(
        index: ComposableIndex,
        root: Path = tempDir,
    ): Path {
        val indexFile = root.resolve("build/composeExpose/composables.json")
        indexFile.parent.createDirectories()
        indexFile.writeText(ComposableIndexJson.encode(index))
        return indexFile
    }

    private fun sampleIndex(
        metadata: IndexMetadata = IndexMetadata(1234L, tempDir.toString(), listOf(":app"), emptyList()),
        extraName: String? = null,
        extraDeclarations: List<ComposableDeclaration> = emptyList(),
    ): ComposableIndex {
        val composables =
            buildList {
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
                        sampleComposable(
                            name = extraName,
                            source = "app/src/main/kotlin/dev/example/Fresh.kt",
                        ),
                    )
                }
                addAll(extraDeclarations)
            }
        return ComposableIndex(metadata, composables)
    }

    private fun sampleComposable(
        name: String,
        source: String,
        module: String = ":app",
        sourceSet: String = "main",
        packageName: String = "dev.example",
        kdocBody: String? = null,
        parameters: List<ComposableParameter> = emptyList(),
        annotations: List<String> = listOf("@Composable"),
        previews: List<PreviewDeclaration> = emptyList(),
    ): ComposableDeclaration =
        ComposableDeclaration(
            id = "$module:$sourceSet:$packageName.$name#",
            module = module,
            sourceSet = sourceSet,
            packageName = packageName,
            name = name,
            visibility = "public",
            source = SourceLocation(source, 4, 1),
            kdoc = kdocBody?.let { Kdoc(it.lineSequence().first(), it) },
            parameters = parameters,
            annotations = annotations,
            previews = previews,
        )
}
