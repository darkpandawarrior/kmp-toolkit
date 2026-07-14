plugins {
    // AGP 9 provides built-in Kotlin support — applying kotlin.android is no longer needed.
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeCompiler)
    `maven-publish`
}

android {
    namespace = "com.siddharth.kmp.provider.paytm"
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
    // Publish only the release variant as com.siddharth.kmp:paytm.
    publishing {
        singleVariant("release") { withSourcesJar() }
    }
}

dependencies {
    // PaymentGateway contract — sibling module in this monorepo.
    implementation(project(":payments-api"))
    // AppLog logging facade — sibling module in this monorepo.
    implementation(project(":common"))
    // Reuses the shared checkout WebView + return-URL relay — sibling module in this monorepo.
    // hosted-webview is a Compose Multiplatform module, so depending on it drags the Compose compiler
    // onto this build, which then requires the Compose runtime on the classpath. This is the only
    // Compose this module needs — PaytmGateway touches only the non-@Composable relay types, it has
    // no Compose UI of its own.
    implementation(project(":provider:hosted-webview"))
    implementation(libs.compose.runtime)

    implementation(libs.core.ktx)
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}

// Declare the release publication; artifactId ("paytm") + the GitHub Packages repo come from the
// shared publishing convention in the root build.gradle.kts.
publishing {
    publications {
        register<MavenPublication>("release") {
            afterEvaluate { from(components["release"]) }
        }
    }
}
