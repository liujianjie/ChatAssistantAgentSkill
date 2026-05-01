// Root project. Per-module configuration is delegated to convention plugins
// in `build-logic/` (introduced in T01-S2). Keep this file minimal.

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
