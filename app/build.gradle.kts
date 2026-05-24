plugins {
    id("stylemirror.android.application")
    id("stylemirror.android.compose")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.stylemirror.app"

    defaultConfig {
        applicationId = "com.stylemirror.app"
        versionCode = 1
        versionName = "0.1.0-mvp"
    }

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
    implementation(project(":infra-net"))
    implementation(project(":infra-llm"))
    implementation(project(":feature-realtime"))
    implementation(project(":feature-import"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    // Room runtime is needed at compile time so the app can reference
    // StyleMirrorDatabase (a RoomDatabase subtype) from DI.
    implementation(libs.room.runtime)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(project(":infra-llm"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
