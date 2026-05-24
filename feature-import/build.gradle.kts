plugins {
    id("stylemirror.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.stylemirror.feature.imports"

    testOptions {
        unitTests {
            isIncludeAndroidResources = false
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":core-data"))
    implementation(project(":infra-llm"))
    implementation(project(":infra-ocr"))
    implementation(project(":feature-realtime"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.snakeyaml)

    testImplementation(libs.junit)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
