plugins {
    alias(libs.plugins.kotlinMultiplatform)
    `maven-publish`
}

// iOS targets ONLY, deliberately. There is no Apple Pay on Android, so this module declares no
// android/jvm/wasmJs target rather than shipping a stub `actual` that compiles everywhere and pays
// nobody. A capability that does not exist on a platform should be absent from that platform's
// classpath, where the compiler can say so, not present and silently inert at runtime.
kotlin {
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // PaymentGateway / WalletGateway contract — sibling module in this monorepo.
            implementation(project(":payments-api"))
            // AppLog + minorToDecimalString — sibling module in this monorepo.
            implementation(project(":common"))
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
