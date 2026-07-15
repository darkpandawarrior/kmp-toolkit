plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    `maven-publish`
}

// First Room module in the monorepo — owns its OWN tiny @Database (one entity), never splices
// into a host app's @Database (Room databases are closed). See OutboxDatabase.kt.
room {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()
    // watchOS targets: consumer Mileway's core:data re-exports SubmitOutbox through commonMain and
    // targets watchos*, so this module must match. appleMain (below) shares the ios/watchos actuals.
    watchosArm64()
    watchosSimulatorArm64()
    watchosDeviceArm64()
    applyDefaultHierarchyTemplate()

    android {
        namespace = "com.siddharth.kmp.offlineoutbox"
        compileSdk = 37
        minSdk = 24
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.room.runtime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.sqlite.bundled)
        }
        // appleMain is the applyDefaultHierarchyTemplate() intermediate set shared by ios* + watchos*
        // — the epochMillis()/buildOutboxDatabase() actuals live here so watchos resolves them too
        // (mirrors Mileway core:data's appleMain Room+BundledSQLiteDriver setup).
        appleMain.dependencies {
            implementation(libs.sqlite.bundled)
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.sqlite.bundled)
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

dependencies {
    // ponytail: no kspCommonMainMetadata entry — that task makes Room's KSP processor generate a
    // concrete `actual object OutboxDatabaseConstructor` straight into commonMain's own metadata
    // compilation, colliding with the hand-written `expect` above ("expect and actual declared in
    // the same module"). Per-platform targets only, matching Room's official KMP setup guide
    // (developer.android.com/kotlin/multiplatform/room) — each platform's actual is generated in
    // its own androidMain/iosMain/jvmMain/watchosMain compilation, not commonMain's.
    add("kspAndroid", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
    add("kspWatchosArm64", libs.room.compiler)
    add("kspWatchosSimulatorArm64", libs.room.compiler)
    add("kspWatchosDeviceArm64", libs.room.compiler)
    add("kspJvm", libs.room.compiler)
}
