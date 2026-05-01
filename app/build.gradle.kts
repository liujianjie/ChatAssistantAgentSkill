plugins {
    id("stylemirror.android.application")
    id("stylemirror.android.compose")
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
}
