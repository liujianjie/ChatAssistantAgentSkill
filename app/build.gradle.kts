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
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":core-data"))
    implementation(project(":infra-net"))
    implementation(project(":infra-llm"))
    implementation(project(":feature-realtime"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
