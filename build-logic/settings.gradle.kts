@file:Suppress("UnstableApiUsage")

// Composite-included build that supplies convention plugins to the root project.
// Kept as a single project (no nested `convention` module) — flat enough for
// MVP scope, and matches the conventions used by 13 modules without ceremony.

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
