plugins {
    id("stylemirror.android.library")
}

android {
    namespace = "com.stylemirror.platform.soul"

    testOptions {
        unitTests {
            isIncludeAndroidResources = false
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":infra-ocr"))
    implementation(project(":feature-realtime"))

    testImplementation(libs.junit)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
