plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("dev.huxleymc.composeexpose")
}

composeExpose {
    backend.set("ksp")
}

android {
    namespace = "dev.huxleymc.composeexpose.demo.dashboard"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation(project(":design-system"))
    implementation(platform("androidx.compose:compose-bom:2026.06.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    ksp("dev.huxleymc.composeexpose:compose-expose-ksp:0.1.0-SNAPSHOT")
}
