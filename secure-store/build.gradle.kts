plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    `maven-publish`
}

// Android + iOS only. `:settings` also has a JVM target, so a JVM actual here would be four lines —
// but nothing asks for one yet, and an unused target is a compile surface with no consumer.
// Add jvm() here and a `SecureStore.jvm.kt` the day a desktop consumer appears.
kotlin {
    iosArm64()
    iosSimulatorArm64()

    android {
        namespace = "com.siddharth.kmp.securestore"
        compileSdk = 37
        minSdk = 24
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            // `:settings` already owns the per-platform crypto — EncryptedSharedPreferences behind an
            // Android Keystore MasterKey, and KeychainSettings on iOS. This module adds the capability
            // flag and the probe on top of its generic `Settings`; it does NOT wrap the Keychain a
            // second time. (`:llm-chat`'s SecureKeyStore proves the same shape.)
            implementation(project(":settings"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            // MapSettings — exercises the probe without a device Keystore or Keychain.
            implementation(libs.multiplatform.settings.test)
        }
    }
}
