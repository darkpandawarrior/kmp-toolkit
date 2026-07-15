import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

// Plugins used by one or more modules — declared here apply-false so each module applies what it needs.
plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidKmpLibrary) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
}

// Shared wasmJs webpack fixes for every module — generated here instead of hand-maintained per
// module (webpack.config.d/ is gitignored). A module is in scope if its build.gradle.kts declares
// a `wasmJs {` target; text-scanned rather than introspecting the Kotlin extension so this doesn't
// need the Kotlin Gradle Plugin API on the root buildscript classpath.
//
// import-meta-shim.js: kotlinx-io's Node.js interop (pulled in transitively by any module
// depending on ktor's JS/Wasm engine, e.g. :network) does `const importMeta = import.meta;` — a
// bare reference webpack's ImportMetaPlugin can't statically rewrite (it only handles
// `import.meta.<prop>` access or destructuring), so it's left as raw syntax in the bundle. karma
// loads that bundle as a plain <script> (not type="module"), and bare `import.meta` is an early
// SyntaxError outside a module context — it fails to parse the whole file, even though the code
// path is Node-only dead code on wasmJs/browser. DefinePlugin substitutes the exact bare AST node
// before codegen. Guarded for `nodejs()` wasmJs targets (no `self` global there).
//
// mjs-esm.js: makes webpack treat `.mjs` imports as ESM so extension-qualified imports resolve
// correctly; harmless/no-op for modules that don't hit the import.meta issue.
//
// Root-caused in :network, 2026-07-15 — see network module history for the debugging trail.
subprojects {
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        val buildScript = project.projectDir.resolve("build.gradle.kts")
        if (buildScript.exists() && buildScript.readText().contains("wasmJs {")) {
            val webpackConfigDir = project.projectDir.resolve("webpack.config.d")
            webpackConfigDir.mkdirs()
            val mjsEsm = """
                config.module.rules.push({
                    test: /\.mjs${'$'}/,
                    resolve: { fullySpecified: false },
                    type: "javascript/esm",
                });
                """.trimIndent() + "\n"
            val importMetaShim = """
                ;(function(config) {
                    const { DefinePlugin } = require("webpack");
                    config.plugins.push(new DefinePlugin({
                        "import.meta": "(typeof self !== 'undefined' ? { url: self.location.href } : { url: '' })",
                    }));
                })(config);
                """.trimIndent() + "\n"
            webpackConfigDir.resolve("mjs-esm.js").let { if (!it.exists() || it.readText() != mjsEsm) it.writeText(mjsEsm) }
            webpackConfigDir.resolve("import-meta-shim.js").let { if (!it.exists() || it.readText() != importMetaShim) it.writeText(importMetaShim) }
        }
    }
}

// Shared publishing for every module — configured once here instead of a copy-pasted block per
// module. Each module still publishes `com.siddharth.kmp:<module>` (+ platform variants) to GitHub
// Packages; credentials come from env (CI) or gradle properties (gitignored) — never committed.
subprojects {
    group = "com.siddharth.kmp"
    version = "1.0.0"
    plugins.withId("maven-publish") {
        extensions.configure<PublishingExtension> {
            // Android-only leaf modules (security + the 11 providers) don't get a publication for
            // free the way KMP modules do (from the kotlinMultiplatform plugin) — register their
            // "release" publication here once instead of copy-pasting this block into each module.
            plugins.withId("com.android.library") {
                publications.register<MavenPublication>("release") {
                    afterEvaluate { from(components["release"]) }
                }
            }
            publications.withType(MavenPublication::class.java).configureEach {
                // KMP modules expose a "kotlinMultiplatform" root publication + one per target
                // (android/jvm/…); the Android-only module (security) exposes a single "release".
                artifactId = when (name) {
                    "kotlinMultiplatform", "release" -> project.name
                    else -> "${project.name}-$name"
                }
            }
            repositories {
                maven {
                    name = "GitHubPackages"
                    url = uri("https://maven.pkg.github.com/darkpandawarrior/kmp-toolkit")
                    credentials {
                        username = System.getenv("GITHUB_ACTOR") ?: providers.gradleProperty("gpr.user").orNull
                        password = System.getenv("GITHUB_TOKEN") ?: providers.gradleProperty("gpr.key").orNull
                    }
                }
            }
        }
    }
}
