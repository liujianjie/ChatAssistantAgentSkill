plugins {
    id("stylemirror.android.library")
    id("stylemirror.android.compose")
}

android {
    namespace = "com.stylemirror.feature.overlay"

    testOptions {
        unitTests {
            isIncludeAndroidResources = false
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":feature-realtime"))
    implementation(libs.kotlinx.coroutines.android)

    // Wiring required to host a ComposeView inside WindowManager (T30.5).
    // setViewTreeLifecycleOwner / setViewTreeViewModelStoreOwner /
    // setViewTreeSavedStateRegistryOwner each live in a separate artifact.
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.savedstate.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
