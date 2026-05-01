package com.stylemirror.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * Compose layer applied on top of either the library or application convention.
 * Kotlin 2.x ships the Compose compiler via the dedicated Gradle plugin —
 * `kotlinCompilerExtensionVersion` is no longer required.
 *
 * Apply order: `stylemirror.android.{library|application}` first, then this one.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

            val androidExt = extensions.findByName("android") as? CommonExtension<*, *, *, *, *, *>
                ?: error(
                    "stylemirror.android.compose must be applied after " +
                        "stylemirror.android.library or stylemirror.android.application",
                )

            androidExt.buildFeatures {
                compose = true
            }

            dependencies {
                val bom = libs.findLibrary("compose-bom").get()
                add("implementation", platform(bom))
                add("androidTestImplementation", platform(bom))

                add("implementation", libs.findLibrary("compose-ui").get())
                add("implementation", libs.findLibrary("compose-ui-graphics").get())
                add("implementation", libs.findLibrary("compose-ui-tooling-preview").get())
                add("implementation", libs.findLibrary("compose-material3").get())

                add("debugImplementation", libs.findLibrary("compose-ui-tooling").get())
                add("debugImplementation", libs.findLibrary("compose-ui-test-manifest").get())

                add("androidTestImplementation", libs.findLibrary("compose-ui-test-junit4").get())
            }
        }
    }
}
