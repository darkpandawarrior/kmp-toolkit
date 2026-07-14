plugins {
    // AGP 9 provides built-in Kotlin support — applying kotlin.android is no longer needed.
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeCompiler)
    `maven-publish`
}

android {
    namespace = "com.siddharth.kmp.provider.cashfree"
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
    // Publish only the release variant as com.siddharth.kmp:cashfree.
    publishing {
        singleVariant("release") { withSourcesJar() }
    }
}

dependencies {
    // PaymentGateway contract — sibling module in this monorepo.
    implementation(project(":payments-api"))
    // AppLog logging facade — sibling module in this monorepo.
    implementation(project(":common"))

    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Cashfree nextgen SDK. `cashfree-pg-api` -> `com.cashfree.pg:api` is the core (headless)
    // artifact: CFPaymentGatewayService, CFSession, CFCheckoutResponseCallback.
    implementation(libs.cashfree.pg.api)

    // Cashfree's drop-in UI (CFDropCheckoutPayment: UPI + cards + net-banking) ships in the separate
    // `com.cashfree.pg:ui` artifact, not `:api` above.
    implementation(libs.cashfree.pg.ui)

    testImplementation(libs.junit)
}

// Declare the release publication; artifactId ("cashfree") + the GitHub Packages repo come from the
// shared publishing convention in the root build.gradle.kts.
publishing {
    publications {
        register<MavenPublication>("release") {
            afterEvaluate { from(components["release"]) }
        }
    }
}
