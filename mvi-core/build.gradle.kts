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
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    android {
        namespace = "com.siddharth.kmp.mvi"
        compileSdk = 37
        minSdk = 24
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.lifecycle.viewmodel)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        groupId = "com.siddharth.kmp"
        artifactId = if (name == "kotlinMultiplatform") "mvi-core" else "mvi-core-$name"
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
