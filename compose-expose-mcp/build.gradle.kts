plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":compose-expose-core"))
    implementation("io.modelcontextprotocol:kotlin-sdk:0.13.0")
    implementation("io.ktor:ktor-server-cio:3.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

    testImplementation(kotlin("test"))
    testImplementation("io.modelcontextprotocol:kotlin-sdk-testing:0.13.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}

application {
    mainClass.set("dev.huxleymc.composeexpose.mcp.ComposeExposeServerMainKt")
}

tasks.test {
    useJUnitPlatform()
}
