@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    `maven-publish`
}


kotlin {
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
    }
}
