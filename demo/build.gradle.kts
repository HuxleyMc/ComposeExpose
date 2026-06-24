plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.spotless)
    id("dev.huxleymc.composeexpose")
}

spotless {
    kotlinGradle {
        target("*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
    }
}

subprojects {
    apply(plugin = "com.diffplug.spotless")

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
}
