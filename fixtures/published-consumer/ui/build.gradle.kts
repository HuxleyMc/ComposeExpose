plugins {
    id("io.github.huxleymc.composeexpose") version "0.1.0-SNAPSHOT"
}

configurations.register("composeExposeProcessor")

dependencies {
    "composeExposeProcessor"("io.github.huxleymc.composeexpose:compose-expose-ksp:0.1.0-SNAPSHOT")
}

tasks.register("verifyComposeExposeProcessorResolution") {
    val processor = configurations.named("composeExposeProcessor")
    inputs.files(processor)
    doLast {
        val files = processor.get().resolve()
        check(files.any { it.name.startsWith("compose-expose-ksp") }) {
            "Expected compose-expose-ksp artifact to resolve from the published repository."
        }
    }
}
