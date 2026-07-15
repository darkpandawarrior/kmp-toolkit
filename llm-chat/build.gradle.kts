plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.kotlinSerialization)
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
        namespace = "com.siddharth.kmp.llmchat"
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
            implementation(libs.ktor.serialization.kotlinx.json)
            // Only for :network's public httpClientEngine() — this module brings its own
            // ktor-client-core/content-negotiation directly rather than :network's createHttpClient
            // wrapper (retry/logging/timeout would change the providers' existing 5s-timeout
            // fire-and-forget behavior). Reusing httpClientEngine() means llm-chat needs no
            // per-platform ktor engine deps of its own — :network already declares OkHttp/Darwin/
            // CIO/Js on its own source sets and they ride the runtime classpath transitively.
            implementation(project(":network"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}
