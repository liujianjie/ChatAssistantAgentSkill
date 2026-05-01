package com.stylemirror.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Shared Android + Kotlin configuration applied by both the library and
 * application conventions. Centralised here so the two plugins agree on
 * compileSdk / minSdk / Java target without drift.
 */
internal fun Project.configureAndroidKotlin(
    extension: CommonExtension<*, *, *, *, *, *>,
) {
    val libs = extensions.getByType<org.gradle.api.artifacts.VersionCatalogsExtension>()
        .named("libs")

    extension.apply {
        compileSdk = libs.findVersion("compile-sdk").get().requiredVersion.toInt()

        defaultConfig {
            minSdk = libs.findVersion("min-sdk").get().requiredVersion.toInt()
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            freeCompilerArgs.addAll(
                "-Xjsr305=strict",
                "-opt-in=kotlin.RequiresOptIn",
            )
        }
    }
}
