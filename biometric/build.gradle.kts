plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    `maven-publish`
}

// Android + iOS only, deliberately. There is no JVM or browser biometric prompt to bind, and a
// stub actual that always answers "unavailable" would be a lie dressed as coverage — the same
// silent-no-op shape `canShareImage` exists to prevent. A target arrives here when a real binding
// arrives with it.
kotlin {
    iosArm64()
    iosSimulatorArm64()

    android {
        namespace = "com.siddharth.kmp.biometric"
        compileSdk = 37
        minSdk = 24
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            // BiometricPrompt + BiometricManager; androidx.fragment (FragmentActivity) rides along.
            implementation(libs.biometric)
            // ContextCompat.getMainExecutor.
            implementation(libs.core.ktx)
            // Dispatchers.Main — BiometricPrompt.authenticate() must be called on the main thread.
            implementation(libs.kotlinx.coroutines.android)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
