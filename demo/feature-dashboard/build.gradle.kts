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
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    ksp(libs.compose.expose.ksp)
}
