plugins {
    id("stylemirror.android.library")
    // Temporary local Jacoco wiring — only to surface a coverage report for
    // PasteInput while T06 is in flight. The proper fix is to centralise this
    // in AndroidLibraryConventionPlugin (tracked as a follow-up to T01); doing
    // it there now would touch build-logic, which T06 is explicitly forbidden
    // from modifying.
    jacoco
}

android {
    namespace = "com.stylemirror.feature.realtime"

    testOptions {
        unitTests {
            isIncludeAndroidResources = false
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(project(":core-domain"))
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(project(":core-domain"))
    testImplementation(libs.junit)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.snakeyaml)
}

// Kotest runs on JUnit 5 — opt the unit-test task in.
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// Local Jacoco report. Run with: ./gradlew :feature-realtime:jacocoTestReport
// Acceptance threshold for T06 (≥ 80% on PasteInput) is verified manually
// from the generated HTML; CI-side enforcement is a later task.
tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    val fileFilter =
        listOf(
            "**/R.class",
            "**/R$*.class",
            "**/BuildConfig.*",
            "**/Manifest*.*",
        )
    val debugTree =
        fileTree("${layout.buildDirectory.get()}/tmp/kotlin-classes/debug") {
            exclude(fileFilter)
        }
    classDirectories.setFrom(files(debugTree))
    sourceDirectories.setFrom(files("src/main/kotlin", "src/main/java"))
    executionData.setFrom(
        fileTree(layout.buildDirectory.get()) {
            include("jacoco/testDebugUnitTest.exec", "outputs/unit_test_code_coverage/**/*.exec")
        },
    )
}
