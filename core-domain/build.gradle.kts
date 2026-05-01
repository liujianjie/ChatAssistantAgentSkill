// Pure-Kotlin domain layer — no Android, no DI, no I/O.
// Carries data classes, sealed errors, and abstraction interfaces consumed by
// every other module.

plugins {
    id("stylemirror.kotlin.jvm")
}

dependencies {
    // SnakeYAML powers the GoldenLoader (T03). Test-only — never reaches main
    // bytecode and does not leak YAML as a domain dependency.
    testImplementation(libs.snakeyaml)
}
