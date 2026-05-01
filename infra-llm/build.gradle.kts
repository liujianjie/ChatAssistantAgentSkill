// infra-llm — LLM provider abstraction + concrete vendor implementations.
// Pure-JVM module: code is OkHttp/Retrofit + kotlinx-serialization, no
// Android API usage. Hosting it on the kotlin-jvm convention buys us
// standard Gradle source sets (so the integrationTest split below is
// idiomatic instead of fighting AGP) and removes the AAR processing
// overhead from CI builds.

plugins {
    id("stylemirror.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":core-domain"))
    // Transport stack (OkHttp / Retrofit / kotlinx-serialization Json) is
    // re-exported by infra-net via `api(...)` so consumers don't double-declare.
    implementation(project(":infra-net"))
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.okhttp.mockwebserver)
    // JUnit5 + Kotest + coroutines-test come from the kotlin-jvm convention.
}

// --- integrationTest source set ---------------------------------------------
// Real-API smoke test for DeepSeekProvider. Lives in a separate source set so
// `./gradlew check` (which CI runs) NEVER touches the live API; the user runs
// `./gradlew :infra-llm:integrationTest` locally with STYLEMIRROR_DEEPSEEK_KEY
// set. The task auto-skips when the env var is missing — never fails CI.
val integrationTestSourceSet =
    sourceSets.create("integrationTest") {
        java.srcDir("src/integrationTest/kotlin")
        resources.srcDir("src/integrationTest/resources")
        compileClasspath += sourceSets["main"].output + sourceSets["test"].output
        runtimeClasspath += output + compileClasspath
    }

configurations["integrationTestImplementation"].extendsFrom(configurations["testImplementation"])
configurations["integrationTestRuntimeOnly"].extendsFrom(configurations["testRuntimeOnly"])

tasks.register<Test>("integrationTest") {
    description = "Live-API smoke test for LLM providers. Requires STYLEMIRROR_DEEPSEEK_KEY."
    group = "verification"
    testClassesDirs = integrationTestSourceSet.output.classesDirs
    classpath = integrationTestSourceSet.runtimeClasspath
    useJUnitPlatform()
    val deepseekKey = providers.environmentVariable("STYLEMIRROR_DEEPSEEK_KEY")
    onlyIf {
        if (!deepseekKey.isPresent) {
            logger.lifecycle(
                ":infra-llm:integrationTest skipped — set STYLEMIRROR_DEEPSEEK_KEY to enable.",
            )
        }
        deepseekKey.isPresent
    }
    environment("STYLEMIRROR_DEEPSEEK_KEY", deepseekKey.orElse("").get())
}
