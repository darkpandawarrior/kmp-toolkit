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
        namespace = "com.siddharth.kmp.result"
        compileSdk = 37
        minSdk = 24
        withHostTest {}
    }

    sourceSets {
        // Pure Kotlin — Result<T, E> and DataError have no coroutine/lifecycle/platform deps.
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
