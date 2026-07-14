plugins {
    // AGP 9 provides built-in Kotlin support — applying kotlin.android is no longer needed.
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeCompiler)
    `maven-publish`
}

android {
    namespace = "com.siddharth.kmp.security"
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
    // Publish only the release variant as com.siddharth.kmp:security.
    publishing {
        singleVariant("release") { withSourcesJar() }
    }
}

dependencies {
    // AppLog logging facade — sibling module in this monorepo.
    implementation(project(":common"))

    implementation(libs.core.ktx)
    implementation(libs.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.compose.ui)

    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.kotlinx.coroutines.core)

    // OkHttp CertificatePinner (PaymentCertificatePinning) — Ktor's OkHttp engine ships OkHttp.
    implementation(libs.ktor.client.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit4)
}

// Declare the release publication; artifactId ("security") + the GitHub Packages repo come from the
// shared publishing convention in the root build.gradle.kts.
publishing {
    publications {
        register<MavenPublication>("release") {
            afterEvaluate { from(components["release"]) }
        }
    }
}
