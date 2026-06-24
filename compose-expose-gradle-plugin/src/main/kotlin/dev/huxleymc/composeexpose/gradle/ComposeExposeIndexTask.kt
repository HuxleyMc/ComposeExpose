package dev.huxleymc.composeexpose.gradle

import dev.huxleymc.composeexpose.core.ComposableIndexJson
import dev.huxleymc.composeexpose.core.SourceComposableExtractor
import org.gradle.api.DefaultTask
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

abstract class ComposeExposeIndexTask : DefaultTask() {
    @get:Input
    abstract val moduleName: Property<String>

    @get:Input
    abstract val sourceSet: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceRoots: ConfigurableFileCollection

    @get:Input
    abstract val projectRoot: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:org.gradle.api.tasks.Internal
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun writeIndex() {
        val roots = sourceRoots.files.map { it.toPath() }.filter { java.nio.file.Files.exists(it) }
        val index = SourceComposableExtractor().extract(
            module = moduleName.get(),
            sourceSet = sourceSet.get(),
            sourceRoots = roots,
            projectRoot = java.nio.file.Path.of(projectRoot.get()),
        )
        val target = outputFile.get().asFile
        target.parentFile.mkdirs()
        target.writeText(ComposableIndexJson.encode(index))
    }
}
