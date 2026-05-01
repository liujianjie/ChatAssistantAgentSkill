package com.stylemirror.buildlogic

import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jlleitschuh.gradle.ktlint.KtlintExtension

/**
 * Static-analysis baseline: ktlint + detekt applied to every module so the
 * 13-module graph stays consistent. Module-specific suppressions go in
 * config/detekt/detekt.yml, not here.
 */
internal fun Project.configureQuality() {
    pluginManager.apply("org.jlleitschuh.gradle.ktlint")
    pluginManager.apply("io.gitlab.arturbosch.detekt")

    extensions.configure<KtlintExtension> {
        android.set(true)
        ignoreFailures.set(false)
        reporters {
            // default reporters are fine; CI parses plain text output
        }
        filter {
            exclude { element -> element.file.path.contains("/build/") }
        }
    }

    extensions.configure<DetektExtension> {
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        buildUponDefaultConfig = true
        allRules = false
        autoCorrect = false
    }
}
