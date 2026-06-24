plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `java-gradle-plugin`
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":compose-expose-core"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    testImplementation(kotlin("test"))
    testImplementation(gradleTestKit())
}

gradlePlugin {
    plugins {
        create("composeExpose") {
            id = "dev.huxleymc.composeexpose"
            implementationClass = "dev.huxleymc.composeexpose.gradle.ComposeExposePlugin"
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
