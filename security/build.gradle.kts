plugins {
    // AGP 9 provides built-in Kotlin support — applying kotlin.android is no longer needed.
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeCompiler)
    `maven-publish`
}

group = "com.siddharth.kmp"
version = "1.0.0"

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

publishing {
    publications {
        register<MavenPublication>("release") {
            afterEvaluate { from(components["release"]) }
            groupId = "com.siddharth.kmp"
            artifactId = "security"
            version = "1.0.0"
        }
    }
    repositories {
        // GitHub Packages. Credentials resolved from env (CI) or gradle properties
        // (local ~/.gradle/gradle.properties, gitignored) — never committed.
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/darkpandawarrior/kmp-toolkit")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: providers.gradleProperty("gpr.user").orNull
                password = System.getenv("GITHUB_TOKEN") ?: providers.gradleProperty("gpr.key").orNull
            }
        }
    }
}
