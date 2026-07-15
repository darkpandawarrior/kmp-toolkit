plugins {
    id("shared.android.library")
    `maven-publish`
}

android {
    namespace = "com.siddharth.kmp.provider.flutterwave"
}

dependencies {
    // PaymentGateway contract — sibling module in this monorepo.
    implementation(project(":payments-api"))
    // AppLog logging facade — sibling module in this monorepo.
    implementation(project(":common"))
    // Reuses the shared checkout WebView + return-URL relay — sibling module in this monorepo.
    // hosted-webview is a Compose Multiplatform module, so depending on it drags the Compose compiler
    // onto this build, which then requires the Compose runtime on the classpath. This is the only
    // Compose this module needs — FlutterwaveGateway touches only the non-@Composable relay types, it
    // has no Compose UI of its own.
    implementation(project(":provider:hosted-webview"))
    implementation(libs.compose.runtime)

    implementation(libs.core.ktx)
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}

