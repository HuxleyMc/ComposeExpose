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

    flavorDimensions += "tier"
    productFlavors {
        create("free") {
            dimension = "tier"
            applicationIdSuffix = ".free"
        }
        create("paid") {
            dimension = "tier"
            applicationIdSuffix = ".paid"
        }
    }
}

dependencies {
    implementation(project(":design-system"))
    implementation(project(":feature-dashboard"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    ksp(libs.compose.expose.ksp)
}
