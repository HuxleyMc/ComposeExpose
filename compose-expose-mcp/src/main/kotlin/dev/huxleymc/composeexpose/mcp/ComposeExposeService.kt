package dev.huxleymc.composeexpose.mcp

import dev.huxleymc.composeexpose.core.ComposableDeclaration
import dev.huxleymc.composeexpose.core.ComposableIndex
import dev.huxleymc.composeexpose.core.ComposableIndexJson
import dev.huxleymc.composeexpose.core.PreviewDeclaration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

@Serializable
data class PreviewSearchResult(
    val composableId: String,
    val composableName: String,
    val preview: PreviewDeclaration,
)

@Serializable
data class IndexStatus(
    val exists: Boolean,
    val isStale: Boolean,
    val generatedAtEpochMillis: Long?,
    val modules: List<String>,
    val sourceRoots: List<String>,
    val newerSources: List<String>,
    val refreshInProgress: Boolean,
)

@Serializable
data class RefreshExecution(
    val exitCode: Int,
    val output: String,
)

@Serializable
data class RefreshResult(
    val success: Boolean,
    val output: String,
    val status: IndexStatus,
)

class ComposeExposeService(
    private val projectRoot: Path,
    private val indexFile: Path = projectRoot.resolve("build/composeExpose/all-composables.json"),
    private val gradleRunner: suspend (List<String>) -> RefreshExecution = { args -> runGradle(projectRoot, args) },
) {
    @Volatile
    private var refreshInProgress = false

    fun searchComposables(
        query: String? = null,
        module: String? = null,
        sourceSet: String? = null,
        limit: Int = DefaultSearchLimit,
    ): List<ComposableDeclaration> {
        val normalizedQuery = query?.trim()?.lowercase().orEmpty()
        return loadIndex().composables
            .asSequence()
            .filter { module == null || it.module == module }
            .filter { sourceSet == null || it.sourceSet == sourceSet }
            .mapNotNull { composable -> composable.toSearchMatch(normalizedQuery) }
            .sortedWith(
                compareBy<SearchMatch> { it.score }
                    .thenBy { it.composable.module }
                    .thenBy { it.composable.packageName }
                    .thenBy { it.composable.name },
            )
            .map { it.composable }
            .take(limit.coerceIn(1, MaxSearchLimit))
            .toList()
    }

    fun getComposable(id: String): ComposableDeclaration? = loadIndex().composables.firstOrNull { it.id == id }

    fun listPreviews(group: String? = null): List<PreviewSearchResult> {
        return loadIndex().composables
            .flatMap { composable ->
                composable.previews.map { preview ->
                    PreviewSearchResult(composable.id, composable.name, preview)
                }
            }
            .filter { group == null || it.preview.group == group }
            .sortedWith(compareBy<PreviewSearchResult> { it.composableName }.thenBy { it.preview.name.orEmpty() })
    }

    fun indexStatus(): IndexStatus {
        return buildIndexStatus(refreshInProgress)
    }

    private fun buildIndexStatus(refreshing: Boolean): IndexStatus {
        if (!Files.exists(indexFile)) {
            return IndexStatus(
                exists = false,
                isStale = true,
                generatedAtEpochMillis = null,
                modules = emptyList(),
                sourceRoots = emptyList(),
                newerSources = emptyList(),
                refreshInProgress = refreshing,
            )
        }
        val index = loadIndex()
        val newerSources = newerSources(index).map { projectRoot.relativize(it).toString() }.sorted()
        return IndexStatus(
            exists = true,
            isStale = newerSources.isNotEmpty(),
            generatedAtEpochMillis = index.metadata.generatedAtEpochMillis,
            modules = index.metadata.modules,
            sourceRoots = index.metadata.sourceRoots,
            newerSources = newerSources,
            refreshInProgress = refreshing,
        )
    }

    suspend fun refreshIndex(module: String? = null): RefreshResult {
        if (module != null && !isValidGradleModulePath(module)) {
            return RefreshResult(
                success = false,
                output = "Invalid Gradle module path: $module",
                status = indexStatus(),
            )
        }
        refreshInProgress = true
        val result = try {
            val task = if (module == null) "composeExposeAggregateIndex" else "${module}:composeExposeIndex"
            val command = listOf("./gradlew", task)
            val execution = gradleRunner(command)
            RefreshResult(
                success = execution.exitCode == 0,
                output = execution.output,
                status = buildIndexStatus(refreshing = false),
            )
        } catch (error: Exception) {
            RefreshResult(
                success = false,
                output = "Failed to refresh ComposeExpose index: ${error.message ?: error::class.simpleName}",
                status = buildIndexStatus(refreshing = false),
            )
        } finally {
            refreshInProgress = false
        }
        return result
    }

    fun loadIndex(): ComposableIndex = ComposableIndexJson.decode(indexFile.readText())

    private fun newerSources(index: ComposableIndex): List<Path> {
        return index.metadata.sourceRoots
            .map { Path.of(it) }
            .filter { Files.exists(it) }
            .flatMap { root ->
                Files.walk(root).use { stream ->
                    stream
                        .filter { it.isRegularFile() && it.toString().endsWith(".kt") }
                        .filter { it.getLastModifiedTime().toMillis() > index.metadata.generatedAtEpochMillis }
                        .toList()
                }
            }
    }

    private data class SearchMatch(
        val composable: ComposableDeclaration,
        val score: Int,
    )

    private fun ComposableDeclaration.toSearchMatch(query: String): SearchMatch? {
        if (query.isBlank()) return SearchMatch(this, 100)
        val name = name.lowercase()
        val packageName = packageName.lowercase()
        val kdocBody = kdoc?.body?.lowercase().orEmpty()
        val score = when {
            name == query -> 0
            name.startsWith(query) -> 10
            name.contains(query) -> 20
            packageName.contains(query) -> 30
            kdocBody.contains(query) -> 40
            else -> return null
        }
        return SearchMatch(this, score)
    }

    private companion object {
        const val DefaultSearchLimit = 20
        const val MaxSearchLimit = 100
        private val modulePathRegex = Regex("^(:[A-Za-z0-9_-]+)+$")

        fun isValidGradleModulePath(module: String): Boolean = modulePathRegex.matches(module)

        suspend fun runGradle(projectRoot: Path, args: List<String>): RefreshExecution = withContext(Dispatchers.IO) {
            val executable = projectRoot.resolve(args.first()).toFile()
            val command = if (executable.exists()) listOf(executable.absolutePath) + args.drop(1) else args
            val process = ProcessBuilder(command)
                .directory(projectRoot.toFile())
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            RefreshExecution(process.waitFor(), output)
        }
    }
}
