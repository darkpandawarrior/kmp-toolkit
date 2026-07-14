plugins {
    // AGP 9 provides built-in Kotlin support — applying kotlin.android is no longer needed.
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeCompiler)
    `maven-publish`
}

android {
    namespace = "com.siddharth.kmp.provider.square"
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
    // Publish only the release variant as com.siddharth.kmp:square.
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

    // In-App Payments Card Entry SDK (sdk.squareup.com/public/android — not on Maven Central).
    // card-entry's own published pom omits nonce-api as a transitive dependency even though
    // CardEntry's API surface (Callback/Card/nonce) requires it at compile time — added explicitly.
    implementation("com.squareup.sdk.in-app-payments:card-entry:1.6.8")
    implementation("com.squareup.sdk.in-app-payments:nonce-api:1.6.8")

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

// Declare the release publication; artifactId ("square") + the GitHub Packages repo come from the
// shared publishing convention in the root build.gradle.kts.
publishing {
    publications {
        register<MavenPublication>("release") {
            afterEvaluate { from(components["release"]) }
        }
    }
}
