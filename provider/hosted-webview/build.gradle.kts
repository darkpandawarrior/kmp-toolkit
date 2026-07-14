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
    // No wasmJs — unlike :designsystem, no consumer here targets a web app (PaymentsLab has none).

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
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.webview.multiplatform)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
