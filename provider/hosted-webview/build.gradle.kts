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
    // wasmJs gets the pure archetype (config/gateway/relay/di) only — the WebView screen lives in
    // webviewMain (android + ios), because compose-webview-multiplatform has no wasm target. A web
    // consumer resolves relay requests itself (e.g. PaymentsLab :web auto-resolves in MOCK_MODE).
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    android {
        namespace = "com.siddharth.kmp.provider.hostedwebview"
        compileSdk = 37
        minSdk = 24
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            // PaymentGateway contract — also re-exports :common's UiText (api dependency), which is
            // all this module needs from :common; no direct :common dep (checked, 0 imports).
            implementation(project(":payments-api"))
            // The Compose compiler plugin runs on every source set of this module (wasm included),
            // and refuses to compile without the runtime on the classpath — so runtime stays common
            // even though only webviewMain has composables.
            implementation(libs.compose.runtime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
        }
        // Intermediate source set for the Compose WebView checkout screen — shared by android + ios,
        // excluded from wasmJs. Declaring explicit dependsOn edges opts this module out of the
        // default hierarchy template, so the ios intermediate is wired by hand below.
        val webviewMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(project(":payments-api"))
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.ui)
                implementation(libs.compose.webview.multiplatform)
            }
        }
        androidMain.get().dependsOn(webviewMain)
        val iosMain = maybeCreate("iosMain").apply { dependsOn(webviewMain) }
        getByName("iosArm64Main").dependsOn(iosMain)
        getByName("iosSimulatorArm64Main").dependsOn(iosMain)
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
