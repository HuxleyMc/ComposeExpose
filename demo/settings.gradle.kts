pluginManagement {
    includeBuild("..")
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ComposeExposeDemo"

includeBuild("..")

include(":app", ":design-system", ":feature-dashboard")
