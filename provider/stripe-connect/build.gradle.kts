plugins {
    // AGP 9 provides built-in Kotlin support — applying kotlin.android is no longer needed.
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeCompiler)
    `maven-publish`
}

android {
    namespace = "com.siddharth.kmp.provider.stripeconnect"
    compileSdk = 37
    defaultConfig { minSdk = 24 }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = false
    }
    // Publish only the release variant as com.siddharth.kmp:stripe-connect.
    publishing {
        singleVariant("release") { withSourcesJar() }
    }
}

dependencies {
    // PaymentGateway-adjacent contract types (ConnectAccount/ConnectBackend/…) — sibling module.
    implementation(project(":payments-api"))
    // AppLog logging facade — sibling module in this monorepo.
    implementation(project(":common"))
    // Reuses the shared checkout WebView + return-URL relay for the mock hosted-OAuth redirect — same
    // idiom as a hosted checkout return-URL (see :provider:paystack, which reuses the identical relay
    // for the same reason).
    implementation(project(":provider:hosted-webview"))
    // Unlike :provider:paystack (relay-only, no UI), this module DOES render its own small
    // StripeConnectCheckoutHost composable (reusing hosted-webview's HostedCheckoutScreen), so it
    // needs the Compose UI artifact (Modifier) on top of the runtime.
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)

    implementation(libs.core.ktx)
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

// Declare the release publication; artifactId ("stripe-connect") + the GitHub Packages repo come
// from the shared publishing convention in the root build.gradle.kts.
publishing {
    publications {
        register<MavenPublication>("release") {
            afterEvaluate { from(components["release"]) }
        }
    }
}
