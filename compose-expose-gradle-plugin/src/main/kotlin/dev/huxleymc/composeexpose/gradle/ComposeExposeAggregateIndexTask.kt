package dev.huxleymc.composeexpose.gradle

import dev.huxleymc.composeexpose.core.ComposableIndexJson
import dev.huxleymc.composeexpose.core.ComposableIndexMerger
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class ComposeExposeAggregateIndexTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val indexFiles: ConfigurableFileCollection

    @get:Input
    abstract val projectRoot: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun mergeIndexes() {
        val indexes = indexFiles.files
            .filter { it.exists() }
            .map { ComposableIndexJson.decode(it.readText()) }
            .filter { it.composables.isNotEmpty() }
        val merged = ComposableIndexMerger.merge(projectRoot.get(), indexes)
        val target = outputFile.get().asFile
        target.parentFile.mkdirs()
        target.writeText(ComposableIndexJson.encode(merged))
    }
}
