package dev.huxleymc.composeexpose.gradle

import com.google.devtools.ksp.gradle.KspExtension
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
        project.excludeGeneratedIndexFromAndroidPackaging()

        project.tasks.register("composeExposeIndex", ComposeExposeIndexTask::class.java) { task ->
            task.group = "compose expose"
            task.description = "Indexes Jetpack Compose composables for MCP discovery."
            task.moduleName.set(extension.moduleName)
            task.sourceSet.set(extension.sourceSet)
            task.backend.set(extension.backend)
            task.sourceRoots.from(extension.sourceRoots.map { roots -> roots.map { project.layout.projectDirectory.dir(it) } })
            task.kspIndexFiles.from(
                project.layout.buildDirectory.asFileTree.matching { patternFilterable ->
                    patternFilterable.include("generated/ksp/**/resources/composeExpose/composables.json")
                    patternFilterable.include("generated/ksp/**/composeExpose/composables.json")
                },
            )
            task.projectRoot.set(project.rootProject.layout.projectDirectory.asFile.absolutePath)
            task.outputDirectory.set(project.layout.buildDirectory.dir("composeExpose"))
            task.outputFile.set(project.layout.buildDirectory.file("composeExpose/composables.json"))
        }

        project.gradle.projectsEvaluated {
            if (extension.backend.get().lowercase() == "ksp") {
                project.configureKspArguments(extension)
                val kspTasks = project.tasks.matching { task ->
                    task.name.startsWith("ksp") && task.name.endsWith("Kotlin")
                }
                project.tasks.named("composeExposeIndex").configure { task ->
                    task.dependsOn(kspTasks)
                }
            }
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

    private fun Project.configureKspArguments(extension: ComposeExposeExtension) {
        pluginManager.withPlugin("com.google.devtools.ksp") {
            val sourceRoots = extension.sourceRoots.get()
                .map { layout.projectDirectory.dir(it).asFile.absolutePath }
                .joinToString(";")
            extensions.configure(KspExtension::class.java) { ksp ->
                ksp.arg("composeExpose.module", extension.moduleName.get())
                ksp.arg("composeExpose.sourceSet", extension.sourceSet.get())
                ksp.arg("composeExpose.projectRoot", rootProject.layout.projectDirectory.asFile.absolutePath)
                ksp.arg("composeExpose.sourceRoots", sourceRoots)
            }
        }
    }

    private fun Project.excludeGeneratedIndexFromAndroidPackaging() {
        pluginManager.withPlugin("com.android.base") {
            val androidExtension = extensions.findByName("android") ?: return@withPlugin
            runCatching {
                val packaging = androidExtension.javaClass.getMethod("getPackaging").invoke(androidExtension)
                val resources = packaging.javaClass.getMethod("getResources").invoke(packaging)
                @Suppress("UNCHECKED_CAST")
                val excludes = resources.javaClass.getMethod("getExcludes").invoke(resources) as MutableSet<String>
                excludes += "composeExpose/composables.json"
            }.onFailure { error ->
                logger.warn("ComposeExpose could not exclude generated KSP index from Android packaging.", error)
            }
        }
    }
}
