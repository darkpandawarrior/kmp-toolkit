@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.roborazzi)
    `maven-publish`
}

kotlin {
    // Must be explicit: the template is applied automatically ONLY while no source set declares its
    // own `dependsOn`. The `composeUiTest` set below declares one, which silently switches the
    // template off — and with it the `iosMain` intermediate that holds the `rememberFormFactor`
    // actual, so the Native compilation fails with "expect declaration has no actual".
    applyDefaultHierarchyTemplate()

    // Added 2026-08-15. Two consumers need a JVM variant of this module: Kursi's `cmp-desktop`
    // (a real Compose Desktop entry point) and Compose Hot Reload, which only runs on a JVM target.
    // Note this is a *Compose* jvm target, distinct from `withHostTest {}` below — that one runs
    // commonTest headlessly against a stubbed android.jar and cannot render.
    jvm()

    iosArm64()
    iosSimulatorArm64()
    wasmJs {
        browser()
        // Required for wasmJsBrowserTest: without a declared executable, the Compose Gradle plugin's
        // Skiko-runtime check fails the test task outright (CMP-4906) since Compose UI can't load its
        // renderer from a bare klib. Also gives this CMP module a real production webpack bundle,
        // matching every other wasmJs-targeting module here.
        binaries.executable()
    }

    android {
        namespace = "com.siddharth.kmp.designsystem"
        compileSdk = 37
        minSdk = 24
        // Runs commonTest on the JVM host (no device) against a stubbed android.jar — the
        // `testDebugUnitTest` surface for pure logic (ThemeController). Kept after `jvm()` was added
        // above: that target renders Compose, this one deliberately cannot.
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            // The @Preview annotation, in commonMain. DesignSystemPreviews.kt records why the
            // annotation this artifact publishes is `androidx.compose.ui.tooling.preview.Preview`
            // and not the deprecated org.jetbrains one.
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.kotlinx.coroutines.core)
            // StepTimeline node icons (Check/Close) — core set only, no need for `-extended`.
            implementation(libs.material.icons.core)
            // StepTimeline/PayloadCard row lists.
            implementation(libs.kotlinx.collections.immutable)
            // AdaptiveNavigationShell. Publishes android/ios/jvm/wasmJs, so it sits in commonMain
            // rather than behind an expect/actual.
            implementation(libs.compose.adaptive.navigation.suite)
            // ZoomableImage. Publishes wasmJs too - checked against the module metadata, since the
            // plan this came from said the wasm publication was contested.
            implementation(libs.zoomable)
            // AiSettingsSection: on-device model management (:ai) and cloud provider/key rows
            // (:llm-chat) both report through :result's AiResult/AiFailure/AiCapabilities, which
            // this module now needs directly too (same targets as :ai/:llm-chat, so no new
            // per-platform actual is needed here).
            implementation(project(":result"))
            implementation(project(":ai"))
            implementation(project(":llm-chat"))
            // WalletAvailability is on WalletPayButton's public signature, so `api` — a consumer
            // that cannot name the enum cannot call the button.
            api(project(":payments-api"))
        }

        // WalletPayButton lives here, NOT in commonMain, and there is no jvm/wasmJs actual on
        // purpose. A device wallet does not exist on desktop or in a browser, so those targets get
        // no declaration at all rather than an empty actual that compiles and silently does
        // nothing — the exact bug class this seam was written to remove. An `expect` in commonMain
        // would force one; an intermediate source set that only android and ios depend on does not,
        // while still making the compiler check that both platforms match.
        // Inherits commonMain's Compose + :payments-api dependencies through dependsOn.
        val walletMain = create("walletMain") { dependsOn(commonMain.get()) }
        androidMain.get().dependsOn(walletMain)
        iosMain.get().dependsOn(walletMain)

        androidMain.dependencies {
            // Google's own button asset. Google Pay's brand guidelines mandate it; a hand-drawn
            // mark fails store review, so this is a hard dependency rather than a convenience.
            implementation(libs.pay.button.compose)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }

        // Screenshot tests are GENERATED from the @Preview functions in commonMain, so there is no
        // hand-maintained list of what is covered: a preview added to DesignSystemPreviews.kt is
        // gated by construction. The alternative — a test class importing each preview by name — is
        // what a consumer app in this family already does, and every preview written after someone
        // last updated that list is silently ungated.
        jvmTest.dependencies {
            implementation(libs.roborazzi.compose.desktop)
            implementation(libs.roborazzi.desktop.preview.scanner)
            implementation(libs.composable.preview.scanner)
            implementation(libs.junit)
        }

        // `runComposeUiTest` renders the composables for real. Deliberately NOT in commonTest: the
        // Android leg is `withHostTest {}`, a bare JVM against the stubbed android.jar, where
        // `Build.FINGERPRINT` is null and the harness NPEs on startup. Making it run there means
        // pulling in Robolectric — a heavy dependency for the one platform Roborazzi already covers
        // elsewhere in the family.
        //
        // iOS and wasm are precisely where this reaches UI that Robolectric/Roborazzi cannot, which
        // is the whole point of gap #5 in the 2026-07-24 absorption note. So it runs exactly there.
        val composeUiTest =
            create("composeUiTest") {
                dependsOn(commonTest.get())
                dependencies {
                    implementation(kotlin("test"))
                    implementation(libs.compose.ui.test)
                }
            }
        iosArm64Test.get().dependsOn(composeUiTest)
        iosSimulatorArm64Test.get().dependsOn(composeUiTest)
        wasmJsTest.get().dependsOn(composeUiTest)
    }
}

// The preview RENDERER — a different artifact from the annotation above, and Android-only: preview
// rendering in Compose Multiplatform is Android tooling underneath, so a commonMain @Preview is
// drawn by the Android renderer and requires this module's `android {}` target to exist.
//
// `androidRuntimeClasspath`, not `debugImplementation`: this module uses AGP 9's
// com.android.kotlin.multiplatform.library plugin (the `android { }` block inside `kotlin { }`),
// which does not create the per-variant debug*/release* configurations the old Android library
// plugin did. Wiring `debugImplementation` here fails with "configuration not found"; wiring
// nothing at all is worse — the previews compile and the IDE gutter silently renders nothing.
dependencies {
    androidRuntimeClasspath(libs.compose.ui.tooling)
}

// Generates a JUnit screenshot test per @Preview found in `packages`, renders each on the jvm()
// target's Compose Desktop renderer and diffs it against a committed golden.
//
// Desktop rather than Robolectric because this module's android target is `withHostTest {}`, which
// cannot render Compose by design. That choice has a consequence worth stating: desktop renders
// through host Skia and Robolectric renders through the Android framework, so these goldens are a
// separate corpus from the consumer apps' and the two can never be cross-checked.
//
//   ./gradlew :designsystem:recordRoborazziJvm   # write goldens
//   ./gradlew :designsystem:verifyRoborazziJvm   # the gate
roborazzi {
    // Goldens live in the repo, not in build/. The default output directory is
    // build/outputs/roborazzi, which is wiped by `clean` and never committed — a gate comparing
    // against images that do not survive a checkout passes on a fresh clone no matter what changed.
    outputDir.set(file("screenshots"))

    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    generateComposePreviewDesktopTests {
        enable = true
        packages = listOf("com.siddharth.kmp.designsystem")
        // The previews are private so they stay out of a published library's API surface. The
        // scanner defaults to skipping private functions, which is why this is not optional here:
        // without it the generated test class has zero parameters, every Roborazzi task passes,
        // and nothing is captured. That green-but-empty state is worse than having no gate, since
        // it reads as coverage. Verified by counting PNGs, not by the build's exit code.
        includePrivatePreviews = true
    }
}
