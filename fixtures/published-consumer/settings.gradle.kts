pluginManagement {
    repositories {
        maven {
            url = uri(providers.gradleProperty("composeExposeRepo").get())
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            url = uri(providers.gradleProperty("composeExposeRepo").get())
        }
        mavenCentral()
    }
}

rootProject.name = "ComposeExposePublishedConsumer"

include(":ui")
