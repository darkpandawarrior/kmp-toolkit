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
        namespace = "com.siddharth.kmp.botspolicy"
        compileSdk = 37
        minSdk = 24
        withHostTest {}
    }

    sourceSets {
        // Generic ISMCTS search shell (Policy/GameRules/Ismcts/SearchBudget) — extracted from
        // Kursi's ai->engine inversion; the domain-specific rollout/leaf-eval/determinization stays
        // in the consuming app. kotlinx-coroutines-core is the one dependency: search() is
        // cancellable, so it needs ensureActive()/CancellationException from that artifact.
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
