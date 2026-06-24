package dev.huxleymc.composeexpose.gradle

import dev.huxleymc.composeexpose.core.ComposableIndexJson
import dev.huxleymc.composeexpose.core.ComposableIndexMerger
import dev.huxleymc.composeexpose.core.SourceComposableExtractor
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Index output embeds generation timestamps and should be regenerated for freshness checks.")
abstract class ComposeExposeIndexTask : DefaultTask() {
    @get:Input
    abstract val moduleName: Property<String>

    @get:Input
    abstract val sourceSet: Property<String>

    @get:Input
    abstract val backend: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceRoots: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val kspIndexFiles: ConfigurableFileCollection

    @get:Input
    abstract val projectRoot: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:org.gradle.api.tasks.Internal
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun writeIndex() {
        val target = outputFile.get().asFile
        target.parentFile.mkdirs()
        val index =
            when (backend.get().lowercase()) {
                "source" -> {
                    val roots =
                        sourceRoots.files.map { it.toPath() }.filter {
                            java.nio.file.Files
                                .exists(it)
                        }
                    SourceComposableExtractor().extract(
                        module = moduleName.get(),
                        sourceSet = sourceSet.get(),
                        sourceRoots = roots,
                        projectRoot =
                            java.nio.file.Path
                                .of(projectRoot.get()),
                    )
                }

                "ksp" -> {
                    val generatedIndexes =
                        kspIndexFiles.files
                            .filter { it.exists() }
                            .map { ComposableIndexJson.decode(it.readText()) }
                    if (generatedIndexes.isEmpty()) {
                        throw GradleException(
                            "ComposeExpose KSP backend did not find generated index output. " +
                                "Apply the com.google.devtools.ksp plugin and add the compose-expose-ksp processor.",
                        )
                    }
                    ComposableIndexMerger.merge(projectRoot.get(), generatedIndexes)
                }

                else -> {
                    throw GradleException("Unsupported ComposeExpose backend '${backend.get()}'. Use 'source' or 'ksp'.")
                }
            }
        target.writeText(ComposableIndexJson.encode(index))
    }
}
