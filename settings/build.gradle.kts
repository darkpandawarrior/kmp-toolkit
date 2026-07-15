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
        namespace = "com.siddharth.kmp.settings"
        compileSdk = 37
        minSdk = 24
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            // Settings is the return type of SecureSettingsFactory.create() — public API, so api().
            // Core artifact ships SharedPreferencesSettings/KeychainSettings/PropertiesSettings.
            api(libs.multiplatform.settings)
        }
        androidMain.dependencies {
            // EncryptedSharedPreferences + MasterKey.AES256_GCM.
            implementation(libs.androidx.security.crypto)
        }
        commonTest.dependencies {
            // kotlin.test flows into jvmTest; the round-trip smoke test lives in src/jvmTest.
            implementation(kotlin("test"))
        }
    }
}
