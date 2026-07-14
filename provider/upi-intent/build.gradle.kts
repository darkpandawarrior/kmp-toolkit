plugins {
    // AGP 9 provides built-in Kotlin support — applying kotlin.android is no longer needed.
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeCompiler)
    `maven-publish`
}

android {
    namespace = "com.siddharth.kmp.provider.upiintent"
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
    // Publish only the release variant as com.siddharth.kmp:upi-intent.
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
    // ActivityResult APIs (ActivityResultContracts) used by pay(); payments-api keeps activity as
    // `implementation`, so this module brings it in directly.
    implementation(libs.activity.compose)
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
}

// Declare the release publication; artifactId ("upi-intent") + the GitHub Packages repo come from
// the shared publishing convention in the root build.gradle.kts.
publishing {
    publications {
        register<MavenPublication>("release") {
            afterEvaluate { from(components["release"]) }
        }
    }
}
