plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("io.github.huxleymc.composeexpose")
}

composeExpose {
    backend.set("ksp")
}

android {
    namespace = "dev.huxleymc.composeexpose.demo.design"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    ksp(libs.compose.expose.ksp)
}
