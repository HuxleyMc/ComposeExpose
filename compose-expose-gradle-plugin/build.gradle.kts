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
    implementation(libs.kotlinx.serialization.json)
    compileOnly(libs.ksp.symbol.processing.gradle.plugin)

    testImplementation(kotlin("test"))
    testImplementation(gradleTestKit())
}

gradlePlugin {
    plugins {
        create("composeExpose") {
            id = "io.github.huxleymc.composeexpose"
            implementationClass = "dev.huxleymc.composeexpose.gradle.ComposeExposePlugin"
            displayName = "ComposeExpose"
            description = "Indexes Jetpack Compose composables for MCP discovery."
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
