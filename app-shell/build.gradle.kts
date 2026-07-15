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
        namespace = "com.siddharth.kmp.appshell"
        compileSdk = 37
        minSdk = 24
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            // LoggingAnalyticsHelper: the noGms/iOS/desktop AnalyticsHelper impl (no proprietary backend).
            implementation(libs.napier)
        }
        androidMain.dependencies {
            implementation(libs.core.ktx)
            implementation(libs.kotlinx.coroutines.android)
            // AndroidLocationTracker: fused location + Task.await(). Same dependency the extracted
            // Mileway core:platform carried unconditionally on both its gms and noGms build flavors —
            // relocated here as-is, not a new coupling.
            implementation(libs.play.services.location)
            implementation(libs.kotlinx.coroutines.play.services)
        }
        iosMain.dependencies {
            // IosAppUpdateManager: public iTunes Lookup API (no backend).
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}
