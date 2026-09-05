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
            // AiResult<T>/AiFailure — shared with :llm-chat so both AI seams report failures the
            // same way; :result is otherwise dependency-free, so this pulls in nothing else.
            implementation(project(":result"))
            // AppLog: the one-time "unimplemented backend" warning on the iOS stubs reuses the
            // existing Napier facade rather than this module inventing its own logging shim.
            implementation(project(":common"))
            // CloudOnDeviceLlm wraps an :llm-chat AiProvider chain as the cloud fallback tier — the
            // same AiResult/AiFailure vocabulary :result already gives both seams, now shared code too.
            implementation(project(":llm-chat"))
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
