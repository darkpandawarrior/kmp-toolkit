plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    `maven-publish`
}

group = "com.siddharth.kmp"
version = "1.0.0"

kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()

    android {
        namespace = "com.siddharth.kmp.network"
        compileSdk = 37
        minSdk = 24
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        groupId = "com.siddharth.kmp"
        artifactId = if (name == "kotlinMultiplatform") "network" else "network-$name"
        version = "1.0.0"
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
