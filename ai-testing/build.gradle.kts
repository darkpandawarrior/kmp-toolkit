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
        namespace = "com.siddharth.kmp.ai.testing"
        compileSdk = 37
        minSdk = 26
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            // `api`, not `implementation`: a consumer only depends on :ai-testing (in its own test
            // source set) and must still see OnDeviceLlm/LlmPart and AiResult/AiFailure/AiCapabilities
            // to construct scripted responses and assert on them — re-declaring both as separate
            // test dependencies in every consuming app would be the same coupling with extra steps.
            api(project(":ai"))
            api(project(":result"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
