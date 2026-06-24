plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `maven-publish`
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":compose-expose-core"))
    implementation(libs.ksp.symbol.processing.api)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
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
