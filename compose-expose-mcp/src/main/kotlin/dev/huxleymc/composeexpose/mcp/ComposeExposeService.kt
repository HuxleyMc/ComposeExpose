package dev.huxleymc.composeexpose.mcp

import dev.huxleymc.composeexpose.core.ComposableDeclaration
import dev.huxleymc.composeexpose.core.ComposableIndex
import dev.huxleymc.composeexpose.core.ComposableIndexJson
import dev.huxleymc.composeexpose.core.IndexMetadata
import dev.huxleymc.composeexpose.core.PreviewDeclaration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
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
data class ModuleSummaryResource(
    val generatedAtEpochMillis: Long,
    val projectRoot: String,
    val sourceRoots: List<String>,
    val modules: List<ModuleSummary>,
)

@Serializable
data class ModuleSummary(
    val module: String,
    val composableCount: Int,
    val previewCount: Int,
    val sourceSets: List<String>,
    val packages: List<String>,
)

@Serializable
data class IndexStatus(
    val exists: Boolean,
    val isStale: Boolean,
    val generatedAtEpochMillis: Long?,
    val ageMillis: Long?,
    val modules: List<String>,
    val sourceRoots: List<String>,
    val newerSources: List<String>,
    val refreshInProgress: Boolean,
    val error: String? = null,
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
    private val currentTimeMillis: () -> Long = { System.currentTimeMillis() },
) {
    private val refreshInProgress = AtomicBoolean(false)

    fun searchComposables(
        query: String? = null,
        module: String? = null,
        sourceSet: String? = null,
        visibility: String? = null,
        hasPreview: Boolean? = null,
        limit: Int = DEFAULT_SEARCH_LIMIT,
    ): List<ComposableDeclaration> {
        val normalizedQuery = query?.trim()?.lowercase().orEmpty()
        val normalizedVisibility = visibility?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        return loadIndex()
            .composables
            .asSequence()
            .filter { module == null || it.module == module }
            .filter { sourceSet == null || it.sourceSet == sourceSet }
            .filter { normalizedVisibility == null || it.visibility.lowercase() == normalizedVisibility }
            .filter { hasPreview == null || it.previews.isNotEmpty() == hasPreview }
            .mapNotNull { composable -> composable.toSearchMatch(normalizedQuery) }
            .sortedWith(
                compareBy<SearchMatch> { it.score }
                    .thenBy { it.composable.module }
                    .thenBy { it.composable.packageName }
                    .thenBy { it.composable.name },
            ).map { it.composable }
            .take(limit.coerceIn(1, MAX_SEARCH_LIMIT))
            .toList()
    }

    fun getComposable(id: String): ComposableDeclaration? = loadIndex().composables.firstOrNull { it.id == id }

    fun listPreviews(
        group: String? = null,
        module: String? = null,
        sourceSet: String? = null,
        annotation: String? = null,
        limit: Int = DEFAULT_SEARCH_LIMIT,
    ): List<PreviewSearchResult> =
        loadIndex()
            .composables
            .asSequence()
            .filter { module == null || it.module == module }
            .filter { sourceSet == null || it.sourceSet == sourceSet }
            .flatMap { composable ->
                composable.previews.map { preview ->
                    PreviewSearchResult(composable.id, composable.name, preview)
                }
            }.filter { group == null || it.preview.group == group }
            .filter { annotation == null || it.preview.annotation == annotation }
            .sortedWith(compareBy<PreviewSearchResult> { it.composableName }.thenBy { it.preview.name.orEmpty() })
            .take(limit.coerceIn(1, MAX_SEARCH_LIMIT))
            .toList()

    fun moduleSummaries(): ModuleSummaryResource {
        val index = loadIndex()
        val modules =
            index.composables
                .groupBy { it.module }
                .map { (module, composables) ->
                    ModuleSummary(
                        module = module,
                        composableCount = composables.size,
                        previewCount = composables.sumOf { it.previews.size },
                        sourceSets = composables.map { it.sourceSet }.distinct().sorted(),
                        packages = composables.map { it.packageName }.distinct().sorted(),
                    )
                }.sortedBy { it.module }
        return ModuleSummaryResource(
            generatedAtEpochMillis = index.metadata.generatedAtEpochMillis,
            projectRoot = index.metadata.projectRoot,
            sourceRoots = index.metadata.sourceRoots,
            modules = modules,
        )
    }

    fun moduleSummary(module: String): ModuleSummary? = moduleSummaries().modules.firstOrNull { it.module == module }

    fun indexStatus(): IndexStatus = buildIndexStatus(refreshInProgress.get())

    private fun buildIndexStatus(refreshing: Boolean): IndexStatus {
        if (!Files.exists(indexFile)) {
            return IndexStatus(
                exists = false,
                isStale = true,
                generatedAtEpochMillis = null,
                ageMillis = null,
                modules = emptyList(),
                sourceRoots = emptyList(),
                newerSources = emptyList(),
                refreshInProgress = refreshing,
            )
        }
        val loadResult = loadIndexSafely()
        if (loadResult.error != null) {
            return IndexStatus(
                exists = true,
                isStale = true,
                generatedAtEpochMillis = null,
                ageMillis = null,
                modules = emptyList(),
                sourceRoots = emptyList(),
                newerSources = emptyList(),
                refreshInProgress = refreshing,
                error = loadResult.error,
            )
        }
        val index = loadResult.index
        val newerSources = newerSources(index).map { it.toStatusPath() }.sorted()
        return IndexStatus(
            exists = true,
            isStale = newerSources.isNotEmpty(),
            generatedAtEpochMillis = index.metadata.generatedAtEpochMillis,
            ageMillis = indexAgeMillis(index.metadata.generatedAtEpochMillis),
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
        if (!refreshInProgress.compareAndSet(false, true)) {
            return RefreshResult(
                success = false,
                output = "ComposeExpose index refresh is already in progress.",
                status = buildIndexStatus(refreshing = true),
            )
        }
        val result =
            try {
                val executions = runRefreshTasks(module)
                RefreshResult(
                    success = executions.all { it.result.exitCode == 0 },
                    output =
                        executions.joinToString("\n\n") { execution ->
                            "> ${execution.command.drop(1).joinToString(" ")}\n${execution.result.output}".trimEnd()
                        },
                    status = buildIndexStatus(refreshing = false),
                )
            } catch (error: Exception) {
                RefreshResult(
                    success = false,
                    output = "Failed to refresh ComposeExpose index: ${error.message ?: error::class.simpleName}",
                    status = buildIndexStatus(refreshing = false),
                )
            } finally {
                refreshInProgress.set(false)
            }
        return result
    }

    fun loadIndex(): ComposableIndex = loadIndexSafely().index

    private fun loadIndexSafely(): IndexLoadResult {
        if (!Files.exists(indexFile)) return IndexLoadResult(index = emptyIndex(), error = null)
        return try {
            IndexLoadResult(index = ComposableIndexJson.decode(indexFile.readText()), error = null)
        } catch (error: Exception) {
            IndexLoadResult(
                index = emptyIndex(),
                error = "Failed to read ComposeExpose index: ${error.message ?: error::class.simpleName}",
            )
        }
    }

    private fun emptyIndex(): ComposableIndex =
        ComposableIndex(
            metadata =
                IndexMetadata(
                    generatedAtEpochMillis = 0L,
                    projectRoot = projectRoot.toString(),
                    modules = emptyList(),
                    sourceRoots = emptyList(),
                ),
            composables = emptyList(),
        )

    private fun indexAgeMillis(generatedAtEpochMillis: Long): Long = (currentTimeMillis() - generatedAtEpochMillis).coerceAtLeast(0L)

    private suspend fun runRefreshTasks(module: String?): List<GradleInvocation> {
        val tasks =
            if (module == null) {
                listOf("composeExposeAggregateIndex")
            } else {
                listOf("$module:composeExposeIndex", "composeExposeAggregateIndex")
            }
        val executions = mutableListOf<GradleInvocation>()
        for (task in tasks) {
            val command = listOf("./gradlew", task)
            val result = gradleRunner(command)
            executions += GradleInvocation(command, result)
            if (result.exitCode != 0) break
        }
        return executions
    }

    private fun newerSources(index: ComposableIndex): List<Path> =
        index.metadata.sourceRoots
            .map { it.toSourceRootPath() }
            .filter { Files.exists(it) }
            .flatMap { root ->
                Files.walk(root).use { stream ->
                    stream
                        .filter { it.isRegularFile() && it.toString().endsWith(".kt") }
                        .filter { it.getLastModifiedTime().toMillis() > index.metadata.generatedAtEpochMillis }
                        .toList()
                }
            }

    private fun String.toSourceRootPath(): Path {
        val path = Path.of(this)
        return if (path.isAbsolute) path.normalize() else projectRoot.resolve(path).normalize()
    }

    private fun Path.toStatusPath(): String {
        val absoluteProjectRoot = projectRoot.toAbsolutePath().normalize()
        val absolutePath = toAbsolutePath().normalize()
        return if (absolutePath.startsWith(absoluteProjectRoot)) {
            absoluteProjectRoot.relativize(absolutePath).toString()
        } else {
            absolutePath.toString()
        }
    }

    private data class SearchMatch(
        val composable: ComposableDeclaration,
        val score: Int,
    )

    private data class GradleInvocation(
        val command: List<String>,
        val result: RefreshExecution,
    )

    private data class IndexLoadResult(
        val index: ComposableIndex,
        val error: String?,
    )

    private fun ComposableDeclaration.toSearchMatch(query: String): SearchMatch? {
        if (query.isBlank()) return SearchMatch(this, 100)
        val name = name.lowercase()
        val packageName = packageName.lowercase()
        val kdocBody = kdoc?.body?.lowercase().orEmpty()
        val parameterText =
            parameters
                .joinToString(" ") { "${it.name} ${it.type}" }
                .lowercase()
        val annotationText = annotations.joinToString(" ").lowercase()
        val previewText =
            previews
                .joinToString(" ") { preview ->
                    buildList {
                        add(preview.annotation)
                        preview.name?.let(::add)
                        preview.group?.let(::add)
                        addAll(preview.arguments.keys)
                        addAll(preview.arguments.values)
                    }.joinToString(" ")
                }.lowercase()
        val score =
            when {
                name == query -> 0
                name.startsWith(query) -> 10
                name.contains(query) -> 20
                packageName.contains(query) -> 30
                kdocBody.contains(query) -> 40
                parameterText.contains(query) -> 50
                annotationText.contains(query) -> 60
                previewText.contains(query) -> 70
                else -> return null
            }
        return SearchMatch(this, score)
    }

    private companion object {
        const val DEFAULT_SEARCH_LIMIT = 20
        const val MAX_SEARCH_LIMIT = 100
        private val MODULE_PATH_REGEX = Regex("^(:[A-Za-z0-9_-]+)+$")

        fun isValidGradleModulePath(module: String): Boolean = MODULE_PATH_REGEX.matches(module)

        suspend fun runGradle(
            projectRoot: Path,
            args: List<String>,
        ): RefreshExecution =
            withContext(Dispatchers.IO) {
                val executable = projectRoot.resolve(args.first()).toFile()
                val command = if (executable.exists()) listOf(executable.absolutePath) + args.drop(1) else args
                val process =
                    ProcessBuilder(command)
                        .directory(projectRoot.toFile())
                        .redirectErrorStream(true)
                        .start()
                val output = process.inputStream.bufferedReader().readText()
                RefreshExecution(process.waitFor(), output)
            }
    }
}
