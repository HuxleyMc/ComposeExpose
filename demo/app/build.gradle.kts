plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("dev.huxleymc.composeexpose")
}

composeExpose {
    backend.set("ksp")
}

android {
    namespace = "dev.huxleymc.composeexpose.demo"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.huxleymc.composeexpose.demo"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    implementation(project(":design-system"))
    implementation(project(":feature-dashboard"))
    implementation(platform("androidx.compose:compose-bom:2026.06.00"))
    implementation("androidx.activity:activity-compose:1.12.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    ksp("dev.huxleymc.composeexpose:compose-expose-ksp:0.1.0-SNAPSHOT")
}
