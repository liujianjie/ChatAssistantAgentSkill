package com.stylemirror.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType

/**
 * Convention for the single :app module — the only application target in the
 * MVP graph. Compose is layered on top via the android.compose convention.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("org.jetbrains.kotlin.android")
            configureQuality()

            extensions.configure<ApplicationExtension> {
                configureAndroidKotlin(this)

                defaultConfig {
                    targetSdk = extensions
                        .getByType<VersionCatalogsExtension>()
                        .named("libs")
                        .findVersion("target-sdk").get().requiredVersion.toInt()
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
            }
        }
    }
}
