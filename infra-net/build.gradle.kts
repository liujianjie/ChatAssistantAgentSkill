// infra-net — shared HTTP/JSON foundation for every infra-* module.
// Owns: OkHttp + Retrofit factories, redacting log interceptor, common
// timeout policy. Pure JVM — no Android API usage — so it lives on the
// kotlin-jvm convention plugin instead of dragging the Android library
// pipeline through every CI build.

plugins {
    id("stylemirror.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    // --- HTTP / JSON stack ---
    api(libs.okhttp)
    api(libs.okhttp.logging)
    api(libs.retrofit)
    api(libs.retrofit.kotlinx.serialization)
    api(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // --- Test extras (JUnit5 + Kotest + coroutines-test come from the
    //     kotlin-jvm convention; we only add MockWebServer here). ---
    testImplementation(libs.okhttp.mockwebserver)
}
