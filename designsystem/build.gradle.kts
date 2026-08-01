@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    `maven-publish`
}


kotlin {
    // Must be explicit: the template is applied automatically ONLY while no source set declares its
    // own `dependsOn`. The `composeUiTest` set below declares one, which silently switches the
    // template off — and with it the `iosMain` intermediate that holds the `rememberFormFactor`
    // actual, so the Native compilation fails with "expect declaration has no actual".
    applyDefaultHierarchyTemplate()

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
        // Runs commonTest on the JVM host (no device) — the CMP leaf has no jvm() target, so this is
        // the runnable unit-test surface (`testDebugUnitTest`) for the pure logic (ThemeController).
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.kotlinx.coroutines.core)
            // StepTimeline node icons (Check/Close) — core set only, no need for `-extended`.
            implementation(libs.material.icons.core)
            // StepTimeline/PayloadCard row lists.
            implementation(libs.kotlinx.collections.immutable)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }

        // `runComposeUiTest` renders the composables for real. Deliberately NOT in commonTest: the
        // Android leg is `withHostTest {}`, a bare JVM against the stubbed android.jar, where
        // `Build.FINGERPRINT` is null and the harness NPEs on startup. Making it run there means
        // pulling in Robolectric — a heavy dependency for the one platform Roborazzi already covers
        // elsewhere in the family.
        //
        // iOS and wasm are precisely where this reaches UI that Robolectric/Roborazzi cannot, which
        // is the whole point of gap #5 in the 2026-07-24 absorption note. So it runs exactly there.
        val composeUiTest by creating {
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
