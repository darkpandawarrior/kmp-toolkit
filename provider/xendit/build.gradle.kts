plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    `maven-publish`
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

    android {
        namespace = "com.siddharth.kmp.provider.xendit"
        compileSdk = 37
        minSdk = 24
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":payments-api"))
            implementation(libs.koin.core)
            implementation(libs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            // Test-only: builds a real client to exercise the mock-flip POST path (production code
            // only ever takes an injected HttpClient — see XenditGateway).
            implementation(project(":network"))
        }
    }
}
