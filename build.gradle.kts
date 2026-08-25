import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

// Plugins used by one or more modules — declared here apply-false so each module applies what it needs.
plugins {
    alias(libs.plugins.vanniktechMavenPublish) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidKmpLibrary) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.detekt) apply false
    // Applied (not `apply false`) — the root project is the aggregator that stitches every module's
    // docs into one site. See the dokka block below.
    alias(libs.plugins.dokka)
}

// Static analysis. This monorepo publishes 37 modules that four apps compile against, and until now
// nothing analysed any of them — a rule violation here reaches every consumer.
//
// `source` points at `src` rather than an enumerated list of source sets. Both Kursi and PaymentsLab
// carried hand-written lists that had silently stopped matching reality: Kursi named five of fifteen
// and left nativeMain, gms, noGms and main unscanned; PaymentsLab omitted wasmJsMain. Adding a target
// adds a source set, and nothing fails when the list is not updated to match, so coverage shrinks
// while the build stays green. `src` cannot drift that way. detekt only reads .kt, and the filter
// below drops generated output.
subprojects {
    apply(plugin = "dev.detekt")
    extensions.configure<dev.detekt.gradle.extensions.DetektExtension> {
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        buildUponDefaultConfig = true
        parallel = true
        // Findings that predate the gate are grandfathered so this lands green; new code is gated.
        baseline = file("detekt-baseline.xml")
        source.setFrom(layout.projectDirectory.dir("src"))
    }
    tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
        exclude("**/build/**", "**/generated/**")
    }
}

// API documentation — one browsable site covering every module, at build/dokka/html.
//
// A library monorepo whose README sells 37 modules had no API reference at all: the only way to
// learn a module's surface was to open its source. Dokka reads the KDoc that is already there, so
// this is closer to switching something on than to writing documentation.
//
// Aggregation is Dokka 2's multi-module model: every subproject applies the plugin and the root
// declares a `dokka(...)` dependency on each, which is what merges them into a single navigable
// site rather than 37 disconnected ones.
subprojects {
    apply(plugin = "org.jetbrains.dokka")
}

dependencies {
    // Derived from `subprojects` rather than listing modules by hand — a new module joins the docs
    // by existing, with no second place to remember to update. The provider:* leaves alone would
    // make a hand-written list 19 entries longer and immediately stale.
    subprojects.forEach { dokka(it) }
}

dokka {
    moduleName.set("kmp-toolkit")
    dokkaPublications.html {
        // Modules with no KDoc-bearing public surface would otherwise fail the build rather than
        // simply producing an empty page.
        failOnWarning.set(false)
    }
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

// Single source of truth for the toolkit version. Declared here because the Central block below
// captures it at configuration time, before the `subprojects` block further down assigns it.
val toolkitVersion = "1.0.0"

// ── Maven Central ─────────────────────────────────────────────────────────────
// A curated first wave only. These four are the genuinely reusable, dependency-light modules;
// the rest stay unpublished until there is a reason for someone else to depend on them.
// One coordinate a reader can paste and resolve is worth more than 19 nobody can fetch.
//
// The published namespace is io.github.darkpandawarrior, which the Central Portal provisions
// automatically for a GitHub-authenticated account. `com.siddharth.kmp` can never be verified:
// Central proves namespace ownership against the matching domain, and siddharth.kmp is not one.
//
// project.group stays com.siddharth.kmp so the four consumer apps' dependencySubstitution rules
// keep resolving to project paths unchanged. Only the PUBLISHED coordinate differs, set below.
//
// Publishing needs three things this repo cannot hold, all configured as CI secrets:
//   ORG_GRADLE_PROJECT_mavenCentralUsername / ...Password  (Central Portal user token)
//   ORG_GRADLE_PROJECT_signingInMemoryKey / ...Password    (ASCII-armoured GPG secret key)
// Until they exist the publish task is simply unavailable; nothing silently half-publishes.
val centralModules = setOf("result", "common", "mvi-core", "network")

subprojects {
    if (name !in centralModules) return@subprojects
    pluginManager.apply("com.vanniktech.maven.publish")

    extensions.configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
        publishToMavenCentral()
        // Signing is mandatory on Central. Only enabled when a key is actually present, so a
        // local `publishToMavenLocal` still works for verification without one.
        if (providers.gradleProperty("signingInMemoryKey").isPresent ||
            System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey") != null
        ) {
            signAllPublications()
        }
        coordinates("io.github.darkpandawarrior", project.name, toolkitVersion)
        pom {
            name.set(project.name)
            description.set(
                "kmp-toolkit ${project.name}: a small, focused Kotlin Multiplatform library " +
                    "extracted from production apps.",
            )
            inceptionYear.set("2026")
            url.set("https://github.com/darkpandawarrior/kmp-toolkit")
            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://github.com/darkpandawarrior/kmp-toolkit/blob/main/LICENSE")
                    distribution.set("repo")
                }
            }
            developers {
                developer {
                    id.set("darkpandawarrior")
                    name.set("Siddharth Pandalai")
                    url.set("https://github.com/darkpandawarrior")
                }
            }
            scm {
                url.set("https://github.com/darkpandawarrior/kmp-toolkit")
                connection.set("scm:git:git://github.com/darkpandawarrior/kmp-toolkit.git")
                developerConnection.set("scm:git:ssh://git@github.com/darkpandawarrior/kmp-toolkit.git")
            }
        }
    }
}

// Shared publishing for every module — configured once here instead of a copy-pasted block per
// module. Each module still publishes `com.siddharth.kmp:<module>` (+ platform variants) to GitHub
// Packages; credentials come from env (CI) or gradle properties (gitignored) — never committed.
subprojects {
    group = "com.siddharth.kmp"
    version = toolkitVersion
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

// CVE fix: ws < 8.21.0 is a high-severity memory-exhaustion DoS. It arrives transitively through
// Ktor's wasm client, whose generated package.json pins `"ws": "8.20.1"` *exactly* — so editing
// kotlin-js-store/wasm/yarn.lock by hand cannot hold: the lock key is the requested descriptor, and
// yarn re-resolves 8.20.1 the moment the two disagree. A Yarn resolution is the only lever.
//
// Wasm has its own Yarn root, separate from JS: `WasmYarnRootExtension`, regenerated by
// `kotlinWasmUpgradeYarnLock` (NOT `kotlinUpgradeYarnLock`, which only touches kotlin-js-store/ and
// reports BUILD SUCCESSFUL without touching the wasm lock at all).
//
// The generated build/wasm/package.json is what carries `resolutions` — if it is stale it keeps an
// empty map and the resolution looks broken when it is not. Verify the fix at the artefact, not the
// exit code: `resolutions` in build/wasm/package.json, `version "8.21.0"` in the lock, and the
// version in build/wasm/node_modules/ws/package.json.
plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnPlugin> {
    the<org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootExtension>().resolution("ws", "8.21.0")
}
