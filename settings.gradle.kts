@file:Suppress("UnstableApiUsage")

pluginManagement {
    includeBuild("build-logic")
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

// 10 application modules per SPEC §3.1. Order is informational only — Gradle
// resolves the dependency graph from each module's build.gradle.kts.
include(":app")
include(":core-domain")
include(":core-data")
include(":feature-import")
include(":feature-realtime")
include(":platform-soul")
include(":platform-stub")
include(":infra-llm")
include(":infra-ocr")
include(":infra-net")
