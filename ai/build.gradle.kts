plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    `maven-publish`
}


kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()

    android {
        namespace = "com.siddharth.kmp.ai"
        compileSdk = 37
        minSdk = 26
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            // aiModule wiring: the on-device LLM tier is bound per platform via onDeviceLlmModule().
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
            // On-device LLM android actuals (detection-ordered). Declared by coordinate to keep the
            // SDKs inside this leaf's owned build file. Model files are downloaded on demand at
            // runtime — never shipped in the repo.
            // 1) ML Kit GenAI Prompt API (Gemini Nano on AICore devices) — experimental.
            implementation("com.google.mlkit:genai-prompt:1.0.0-beta2")
            // 2) MediaPipe LLM Inference (Gemma) — broader device coverage.
            implementation("com.google.mediapipe:tasks-genai:0.10.35")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
