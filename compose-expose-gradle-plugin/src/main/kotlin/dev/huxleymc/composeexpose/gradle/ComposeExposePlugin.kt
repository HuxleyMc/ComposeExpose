package dev.huxleymc.composeexpose.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

class ComposeExposePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("composeExpose", ComposeExposeExtension::class.java)
        extension.moduleName.convention(project.path)
        extension.sourceRoots.convention(
            listOf(
                "src/main/kotlin",
                "src/main/java",
            ),
        )

        project.tasks.register("composeExposeIndex", ComposeExposeIndexTask::class.java) { task ->
            task.group = "compose expose"
            task.description = "Indexes Jetpack Compose composables for MCP discovery."
            task.moduleName.set(extension.moduleName)
            task.sourceSet.set(extension.sourceSet)
            task.sourceRoots.from(extension.sourceRoots.map { roots -> roots.map { project.layout.projectDirectory.dir(it) } })
            task.projectRoot.set(project.rootProject.layout.projectDirectory.asFile.absolutePath)
            task.outputDirectory.set(project.layout.buildDirectory.dir("composeExpose"))
            task.outputFile.set(project.layout.buildDirectory.file("composeExpose/composables.json"))
        }

        if (project == project.rootProject) {
            val aggregate = project.tasks.register(
                "composeExposeAggregateIndex",
                ComposeExposeAggregateIndexTask::class.java,
            ) { task ->
                task.group = "compose expose"
                task.description = "Merges ComposeExpose indexes from all indexed projects."
                task.projectRoot.set(project.rootProject.layout.projectDirectory.asFile.absolutePath)
                task.outputFile.set(project.layout.buildDirectory.file("composeExpose/all-composables.json"))
            }

            project.gradle.projectsEvaluated {
                val indexTasks = project.allprojects
                    .mapNotNull { candidate -> candidate.tasks.findByName("composeExposeIndex") as? ComposeExposeIndexTask }
                    .filter { indexTask -> indexTask.project != project || project.subprojects.isEmpty() }

                aggregate.configure { task ->
                    task.dependsOn(indexTasks)
                    task.indexFiles.from(indexTasks.map { it.outputFile })
                }
            }
        }
    }
}
