import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.plugins.signing.SigningExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.spotless)
}

group = "io.github.huxleymc.composeexpose"
version = providers.gradleProperty("composeExposeVersion").orElse("0.1.0-SNAPSHOT").get()

fun publicationName(artifactId: String): String =
    when (artifactId) {
        "compose-expose-core" -> "ComposeExpose Core"
        "compose-expose-gradle-plugin" -> "ComposeExpose Gradle Plugin"
        "compose-expose-ksp" -> "ComposeExpose KSP Processor"
        "compose-expose-mcp" -> "ComposeExpose MCP Server"
        "io.github.huxleymc.composeexpose.gradle.plugin" -> "ComposeExpose Gradle Plugin Marker"
        else -> artifactId
    }

fun publicationDescription(artifactId: String): String =
    when (artifactId) {
        "compose-expose-core" -> "Shared ComposeExpose index schema and extraction utilities."
        "compose-expose-gradle-plugin" -> "Gradle plugin that indexes Jetpack Compose composables for MCP discovery."
        "compose-expose-ksp" -> "KSP processor that generates ComposeExpose composable indexes."
        "compose-expose-mcp" -> "MCP server for querying generated ComposeExpose indexes."
        "io.github.huxleymc.composeexpose.gradle.plugin" -> "Gradle plugin marker for ComposeExpose."
        else -> "ComposeExpose publication."
    }

spotless {
    kotlin {
        target("fixtures/**/*.kt")
        ktlint(libs.versions.ktlint.get())
    }

    kotlinGradle {
        target("*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
    }
}

subprojects {
    group = rootProject.group
    version = rootProject.version

    apply(plugin = "com.diffplug.spotless")

    plugins.withId("java-base") {
        extensions.configure<JavaPluginExtension>("java") {
            withSourcesJar()
            withJavadocJar()
        }
    }

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            target("src/**/*.kt")
            targetExclude("**/build/**", "**/generated/**")
            ktlint(
                rootProject.libs.versions.ktlint
                    .get(),
            )
        }

        kotlinGradle {
            target("*.gradle.kts")
            ktlint(
                rootProject.libs.versions.ktlint
                    .get(),
            )
        }
    }

    plugins.withId("maven-publish") {
        pluginManager.apply("signing")

        extensions.configure<PublishingExtension>("publishing") {
            repositories {
                maven {
                    name = "localTest"
                    url =
                        rootProject.layout.buildDirectory
                            .dir("local-maven")
                            .get()
                            .asFile
                            .toURI()
                }
                maven {
                    name = "centralBundle"
                    url =
                        rootProject.layout.buildDirectory
                            .dir("central-portal/repository")
                            .get()
                            .asFile
                            .toURI()
                }
                providers.gradleProperty("composeExposePublishUrl").orNull?.let { publishUrl ->
                    maven {
                        name = "remote"
                        url = uri(publishUrl)
                        credentials {
                            username = providers.gradleProperty("composeExposePublishUsername").orNull
                                ?: System.getenv("COMPOSE_EXPOSE_PUBLISH_USERNAME")
                            password = providers.gradleProperty("composeExposePublishPassword").orNull
                                ?: System.getenv("COMPOSE_EXPOSE_PUBLISH_PASSWORD")
                        }
                    }
                }
            }

            publications.withType<MavenPublication>().configureEach {
                pom {
                    name.set(provider { publicationName(artifactId) })
                    description.set(provider { publicationDescription(artifactId) })
                    url.set("https://github.com/HuxleyMc/ComposeExpose")
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                            distribution.set("repo")
                        }
                    }
                    developers {
                        developer {
                            id.set("huxleymc")
                            name.set("Huxley McGuffin")
                        }
                    }
                    scm {
                        connection.set("scm:git:git://github.com/HuxleyMc/ComposeExpose.git")
                        developerConnection.set("scm:git:ssh://git@github.com/HuxleyMc/ComposeExpose.git")
                        url.set("https://github.com/HuxleyMc/ComposeExpose")
                    }
                }
            }
        }

        extensions.configure<SigningExtension>("signing") {
            val signingKey = providers.gradleProperty("signingInMemoryKey").orNull
            val signingPassword = providers.gradleProperty("signingInMemoryKeyPassword").orNull
            setRequired {
                !version.toString().endsWith("SNAPSHOT") &&
                    gradle.taskGraph.allTasks.any { it.name.startsWith("publish") }
            }
            if (!signingKey.isNullOrBlank()) {
                useInMemoryPgpKeys(signingKey, signingPassword.orEmpty())
            }
            sign(extensions.getByType<PublishingExtension>().publications)
        }
    }
}
