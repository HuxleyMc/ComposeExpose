pluginManagement {
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

rootProject.name = "ComposeExpose"

include(
    ":compose-expose-core",
    ":compose-expose-gradle-plugin",
    ":compose-expose-ksp",
    ":compose-expose-mcp",
)
