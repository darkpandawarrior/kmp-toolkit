plugins {
    id("shared.android.library")
    `maven-publish`
}

android {
    namespace = "com.siddharth.kmp.provider.cashfree"
}

dependencies {
    // PaymentGateway contract — sibling module in this monorepo.
    implementation(project(":payments-api"))
    // AppLog logging facade — sibling module in this monorepo.
    implementation(project(":common"))

    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Cashfree nextgen SDK. `cashfree-pg-api` -> `com.cashfree.pg:api` is the core (headless)
    // artifact: CFPaymentGatewayService, CFSession, CFCheckoutResponseCallback.
    implementation(libs.cashfree.pg.api)

    // Cashfree's drop-in UI (CFDropCheckoutPayment: UPI + cards + net-banking) ships in the separate
    // `com.cashfree.pg:ui` artifact, not `:api` above.
    implementation(libs.cashfree.pg.ui)

    testImplementation(libs.junit)
}

