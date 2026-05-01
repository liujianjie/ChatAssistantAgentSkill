package com.stylemirror.buildlogic

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Convention for pure-Kotlin (non-Android) modules — e.g. core-domain.
 *
 * Pinned at JVM 17 so domain/business code is testable on a desktop JDK without
 * requiring the Android SDK. The same toolchain is reused by the Android
 * conventions to keep bytecode targets consistent across the 10-module graph.
 *
 * Also wires the standard JVM test stack (JUnit Platform + Kotest 5 + Turbine
 * + coroutines-test) and Jacoco so every JVM module reports coverage uniformly.
 */
class KotlinJvmConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            pluginManager.apply("jacoco")
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

            configureJvmTesting()
            configureJacoco()
        }
    }
}

private fun Project.configureJvmTesting() {
    val libs = rootProject.extensions.getByType(
        org.gradle.api.artifacts.VersionCatalogsExtension::class.java,
    ).named("libs")

    fun lib(alias: String) = libs.findLibrary(alias).get()

    dependencies {
        add("testImplementation", lib("junit"))
        add("testImplementation", lib("kotest-runner-junit5"))
        add("testImplementation", lib("kotest-assertions-core"))
        add("testImplementation", lib("kotest-property"))
        add("testImplementation", lib("turbine"))
        add("testImplementation", lib("kotlinx-coroutines-test"))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events =
                setOf(
                    TestLogEvent.FAILED,
                    TestLogEvent.SKIPPED,
                    TestLogEvent.STANDARD_ERROR,
                )
            exceptionFormat = TestExceptionFormat.FULL
            showStackTraces = true
            showCauses = true
        }
    }
}

private fun Project.configureJacoco() {
    extensions.configure<JacocoPluginExtension> {
        toolVersion = "0.8.12"
    }

    tasks.withType<JacocoReport>().configureEach {
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
        dependsOn(tasks.named("test"))
    }
}