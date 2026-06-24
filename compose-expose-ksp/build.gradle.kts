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
    implementation("com.google.devtools.ksp:symbol-processing-api:2.3.9")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    repositories {
        maven {
            name = "localTest"
            url = rootProject.layout.buildDirectory.dir("local-maven").get().asFile.toURI()
        }
    }
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name.set("ComposeExpose KSP Processor")
                description.set("KSP processor that generates ComposeExpose composable indexes.")
            }
        }
    }
}
