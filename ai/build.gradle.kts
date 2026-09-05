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
        namespace = "com.siddharth.kmp.ai"
        compileSdk = 37
        minSdk = 26
        withHostTest {}
        // Device-verifies MlKitGenAiOnDeviceLlm/MediaPipeOnDeviceLlm against the real Android SDKs
        // (real Context, real filesystem, real ML Kit/MediaPipe client) instead of only compiling
        // against them. Source set is "androidDeviceTest" (this plugin's actual default name for
        // what AGP's older `com.android.library` called `androidTest`) — its default sourceSetTree
        // does NOT pull in commonTest, so kotlin-test is declared directly below instead.
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            // StructuredOutput<T>'s tolerant JSON parse of a model reply — same dependency
            // :llm-chat already carries for its own response parsing, wired here for :ai's use.
            implementation(libs.kotlinx.serialization.json)
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
        // ResumableModelDownloader is plain JDK (HttpURLConnection/File) with no Android framework
        // dependency, so its Range/resume/gzip-reject behavior is verified here against a real
        // com.sun.net.httpserver.HttpServer (JDK, no new test dependency) rather than mocked.
        getByName("androidHostTest") {
            dependencies {
                implementation(libs.junit)
            }
        }
        // MlKitGenAiOnDeviceLlmTest/MediaPipeOnDeviceLlmTest: real Context via
        // androidx.test.core, run through androidx.test.runner's AndroidJUnitRunner. No fakes here
        // — the whole point is exercising the actual ML Kit/MediaPipe SDK calls on a real device.
        getByName("androidDeviceTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.androidx.test.runner)
                implementation(libs.androidx.test.core)
                implementation(libs.androidx.test.ext.junit)
            }
        }
    }
}
