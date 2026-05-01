// infra-net — shared HTTP/JSON foundation for every infra-* module.
// Owns: OkHttp + Retrofit factories, redacting log interceptor, common
// timeout policy. Does NOT own provider-specific API types — those live
// in infra-llm (T05) so this module stays vendor-neutral.

plugins {
    id("stylemirror.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.stylemirror.infra.net"
}

dependencies {
    // --- HTTP / JSON stack ---
    api(libs.okhttp)
    api(libs.okhttp.logging)
    api(libs.retrofit)
    api(libs.retrofit.kotlinx.serialization)
    api(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // --- Unit test stack (JUnit5 + Kotest + MockWebServer) ---
    testImplementation(libs.junit)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}

// Kotest 5 runs on JUnit Platform; the Android library convention does not
// configure this for us, so we wire it here.
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
