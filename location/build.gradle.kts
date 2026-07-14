plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    `maven-publish`
}


kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    android {
        namespace = "com.siddharth.kmp.location"
        compileSdk = 37
        minSdk = 24
        withHostTest {}
    }

    sourceSets {
        // Pure GPS-track math — Kalman smoothing, path simplification, dynamic polling interval,
        // live fix-quality scoring. No platform/coroutine deps; works on every target.
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
