@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    `maven-publish`
}

kotlin {
    applyDefaultHierarchyTemplate()

    jvm()
    iosArm64()
    iosSimulatorArm64()
    wasmJs {
        browser()
        // CMP-4906: without a declared executable the Compose plugin's Skiko-runtime check fails
        // `check` outright, because Compose UI cannot load its renderer from a bare klib. Same fix
        // :designsystem already carries.
        binaries.executable()
    }

    android {
        namespace = "com.siddharth.kmp.charts"
        compileSdk = 37
        minSdk = 24
        withHostTest {}
    }

    sourceSets {
        // Deliberately NOT in :designsystem. That module is consumed by Mileway's Wear target and
        // a chart library has no business in a watch build. :charts depends on :designsystem for
        // tokens; never the reverse.
        //
        // Vico 3.3.0 DOES publish wasmJs - verified against the published Gradle module metadata,
        // not the changelog. The plan this came from claimed it did not, which would have bought
        // us an expect/actual and a hand-drawn Canvas fallback for nothing.
        commonMain.dependencies {
            implementation(project(":designsystem"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.vico.compose)
            implementation(libs.vico.compose.m3)
            implementation(libs.kotlinx.collections.immutable)
        }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}
