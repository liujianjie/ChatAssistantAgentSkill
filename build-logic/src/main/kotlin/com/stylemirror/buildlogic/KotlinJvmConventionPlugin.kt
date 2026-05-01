package com.stylemirror.buildlogic

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Convention for pure-Kotlin (non-Android) modules — e.g. core-domain.
 *
 * Pinned at JVM 17 so domain/business code is testable on a desktop JDK without
 * requiring the Android SDK. The same toolchain is reused by the Android
 * conventions to keep bytecode targets consistent across the 13-module graph.
 */
class KotlinJvmConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            configureQuality()

            extensions.configure<JavaPluginExtension> {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
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
    }
}
