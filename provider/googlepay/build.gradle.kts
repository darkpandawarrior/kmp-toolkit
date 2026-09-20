plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.kotlinSerialization)
    `maven-publish`
}

// KMP with exactly one target, and that is not a contradiction. There is no Google Pay SDK for iOS,
// so this module declares NO iosMain — the split exists to keep the config and the request JSON in
// commonMain, where they are pure data with no Play Services on the classpath, and to let the
// request-shape tests run on the host without Robolectric.
kotlin {
    android {
        namespace = "com.siddharth.kmp.provider.googlepay"
        compileSdk = 37
        minSdk = 24
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            // PaymentGateway / WalletGateway contract — sibling module in this monorepo.
            implementation(project(":payments-api"))
            // AppLog logging facade + minorToDecimalString — sibling module in this monorepo.
            implementation(project(":common"))
            // Replaces org.json: that is an Android system class, which is what pinned the request
            // builder to androidMain and forced Robolectric into a pure-JSON unit test.
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.core.ktx)
            implementation(libs.activity.compose)
            implementation(libs.koin.android)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.play.services.wallet)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
