plugins {
    id("shared.android.library")
    `maven-publish`
}

android {
    namespace = "com.siddharth.kmp.provider.stripeconnect"
}

dependencies {
    // PaymentGateway-adjacent contract types (ConnectAccount/ConnectBackend/…) — sibling module.
    implementation(project(":payments-api"))
    // AppLog logging facade — sibling module in this monorepo.
    implementation(project(":common"))
    // Reuses the shared checkout WebView + return-URL relay for the mock hosted-OAuth redirect — same
    // idiom as a hosted checkout return-URL (see :provider:paystack, which reuses the identical relay
    // for the same reason).
    implementation(project(":provider:hosted-webview"))
    // Unlike :provider:paystack (relay-only, no UI), this module DOES render its own small
    // StripeConnectCheckoutHost composable (reusing hosted-webview's HostedCheckoutScreen), so it
    // needs the Compose UI artifact (Modifier) on top of the runtime.
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)

    implementation(libs.core.ktx)
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

