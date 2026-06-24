plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
    `maven-publish`
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":compose-expose-core"))
    implementation(libs.mcp.kotlin.sdk)
    implementation(libs.ktor.server.cio)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.mcp.kotlin.sdk.testing)
    testImplementation(libs.kotlinx.coroutines.test)
}

application {
    mainClass.set("dev.huxleymc.composeexpose.mcp.ComposeExposeServerMainKt")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
