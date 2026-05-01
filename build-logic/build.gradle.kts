plugins {
    `kotlin-dsl`
}

// build-logic itself runs on the same Java 17 toolchain as the rest of the
// project. Kotlin DSL precompiled scripts inherit JVM target from the toolchain.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    // AGP / Kotlin Gradle plugins are needed only at compile time inside the
    // convention plugins themselves — consumers pick them up via the version
    // catalog plugin aliases.
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.kotlin.compose.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
    compileOnly(libs.ktlint.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kotlinJvm") {
            id = "stylemirror.kotlin.jvm"
            implementationClass = "com.stylemirror.buildlogic.KotlinJvmConventionPlugin"
        }
        register("androidLibrary") {
            id = "stylemirror.android.library"
            implementationClass = "com.stylemirror.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidApplication") {
            id = "stylemirror.android.application"
            implementationClass = "com.stylemirror.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidCompose") {
            id = "stylemirror.android.compose"
            implementationClass = "com.stylemirror.buildlogic.AndroidComposeConventionPlugin"
        }
    }
}
