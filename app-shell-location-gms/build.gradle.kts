plugins {
    id("shared.android.library")
    `maven-publish`
}

android {
    namespace = "com.siddharth.kmp.appshell.gms"
}

dependencies {
    // LocationTracker contract — sibling module in this monorepo.
    implementation(project(":app-shell"))

    implementation(libs.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    // shared.android.library applies the Compose-compiler plugin unconditionally (same as
    // :security and the 11 payment-provider leaf modules); this module has no @Composable code,
    // the runtime dep is here only to satisfy that plugin, not a real usage.
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.compose.ui)

    // GmsFusedLocationTracker: fused location + Task.await(). Opt-in only — a consumer's gms
    // flavor depends on this module and binds it in place of app-shell's plain
    // AndroidLocationTracker; a noGms/FOSS flavor never pulls this module in at all.
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
