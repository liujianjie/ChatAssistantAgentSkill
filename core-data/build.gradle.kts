// core-data — persistence + secure storage. Deliberately depends only on
// core-domain types so the data layer cannot reach upward into features.

plugins {
    id("stylemirror.android.library")
}

android {
    namespace = "com.stylemirror.core.data"

    // Robolectric needs Android resources merged into the JVM test classpath.
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    // SecureKeyStore contract lives in core-domain — never reach upward.
    implementation(project(":core-domain"))

    // Coroutines for off-main-thread keystore I/O.
    implementation(libs.kotlinx.coroutines.android)

    // SecureKeyStore implementation — backed by AndroidX EncryptedSharedPrefs.
    implementation(libs.androidx.security.crypto)

    // --- Unit test stack (Robolectric so we can exercise the real Android
    //     SharedPreferences + Tink keystore on a desktop JVM). RobolectricTestRunner
    //     is a JUnit4 runner, so we deliberately stay on JUnit4 here and only
    //     pull Kotest in as an *assertion* library (not as a test engine). ---
    testImplementation(libs.junit)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.robolectric)
}
