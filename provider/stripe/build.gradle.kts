plugins {
    // AGP 9 provides built-in Kotlin support — applying kotlin.android is no longer needed.
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeCompiler)
    `maven-publish`
}

android {
    namespace = "com.siddharth.kmp.provider.stripe"
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
    // Publish only the release variant as com.siddharth.kmp:stripe.
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

    // Stripe PaymentSheet drives the card/wallet UI; play-services-wallet is required for the
    // Google-Pay-via-Stripe path (Google Pay rides Stripe as the gateway of record).
    implementation(libs.stripe.paymentsheet)
    implementation(libs.play.services.wallet)
}

// Declare the release publication; artifactId ("stripe") + the GitHub Packages repo come from the
// shared publishing convention in the root build.gradle.kts.
publishing {
    publications {
        register<MavenPublication>("release") {
            afterEvaluate { from(components["release"]) }
        }
    }
}
