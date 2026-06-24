plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `java-gradle-plugin`
    `maven-publish`
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":compose-expose-core"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    compileOnly("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.9")

    testImplementation(kotlin("test"))
    testImplementation(gradleTestKit())
}

gradlePlugin {
    plugins {
        create("composeExpose") {
            id = "dev.huxleymc.composeexpose"
            implementationClass = "dev.huxleymc.composeexpose.gradle.ComposeExposePlugin"
            displayName = "ComposeExpose"
            description = "Indexes Jetpack Compose composables for MCP discovery."
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
