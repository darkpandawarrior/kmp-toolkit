plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    `maven-publish`
}


kotlin {
    // Targets deliberately match :settings — android/ios/jvm, NO wasmJs.
    //
    // multiplatform-settings itself ships a wasmJs target, so this module *could* compile for the
    // browser. It must not: the wasm backing store is `localStorage`, which is plaintext and readable
    // by any script on the origin. A TokenStore that silently degrades from Keychain to localStorage
    // when you add a web target is a security regression wearing the costume of wider coverage.
    // A browser app should hold its session in an HttpOnly cookie the JS never sees.
    jvm()
    iosArm64()
    iosSimulatorArm64()

    android {
        namespace = "com.siddharth.kmp.auth"
        compileSdk = 37
        minSdk = 24
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            // Settings is a constructor parameter — public API, so api().
            //
            // Depends on multiplatform-settings directly rather than on project(":settings"). The
            // consumer picks the backing Settings: SecureSettingsFactory().create() in production,
            // MapSettings() in tests. Depending on :settings would force every consumer to take the
            // encrypted implementation even where a fake is wanted, and would couple two modules
            // that have no reason to know about each other.
            api(libs.multiplatform.settings)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            // MapSettings — the in-memory fake, same one Mileway's own auth tests already use.
            implementation(libs.multiplatform.settings.test)
        }
    }
}
