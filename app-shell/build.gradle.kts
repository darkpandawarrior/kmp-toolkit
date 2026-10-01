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
            // AndroidLocationTracker: plain android.location.LocationManager, zero Play Services —
            // this is what every consumer gets, including a noGms/F-Droid classpath. The fused
            // provider is opt-in only, in the sibling `app-shell-location-gms` module, so
            // play-services-location never lands here unconditionally (was the root cause of
            // Doori's noGms dependencyGuard leak; see that module's kdoc).
            // AndroidFilePicker: KmpFile byte I/O only — calf-file-picker itself is @Composable-only
            // (rememberFilePickerLauncher/rememberFileSaverLauncher) and this module has no Compose
            // dependency, so the launcher is bridged in by the host's Compose layer; see AndroidFilePicker.
            implementation(libs.calf.io)
            // AndroidForwardGeocoder/AndroidPlaceAutocomplete. android+ios only by design (see the
            // libs.versions.toml note on compass's `*-mobile` artifacts) — never add these to
            // commonMain, or the default hierarchy template leaks them into this module's jvm() target.
            implementation(libs.compass.core)
            implementation(libs.compass.geocoder)
            implementation(libs.compass.geocoder.mobile)
            implementation(libs.compass.autocomplete)
            implementation(libs.compass.autocomplete.mobile)
        }
        iosMain.dependencies {
            // IosAppUpdateManager: public iTunes Lookup API (no backend).
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.darwin)
            // IosFilePicker: KmpFile byte I/O only — see the androidMain comment on calf-io.
            implementation(libs.calf.io)
            // IosForwardGeocoder/IosPlaceAutocomplete — see the androidMain comment on compass-*-mobile.
            implementation(libs.compass.core)
            implementation(libs.compass.geocoder)
            implementation(libs.compass.geocoder.mobile)
            implementation(libs.compass.autocomplete)
            implementation(libs.compass.autocomplete.mobile)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}
