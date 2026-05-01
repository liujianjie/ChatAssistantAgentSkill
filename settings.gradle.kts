@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
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

rootProject.name = "style-mirror-copilot"

// Modules will be registered in T01-S3 (Slice 3).
// Slice 1 keeps the root build runnable on its own so `./gradlew help` works
// before any module / convention plugin is wired up.
