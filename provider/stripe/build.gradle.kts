plugins {
    id("shared.android.library")
    `maven-publish`
}

android {
    namespace = "com.siddharth.kmp.provider.stripe"
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

    // Stripe PaymentSheet drives the card/wallet UI; play-services-wallet is required for the
    // Google-Pay-via-Stripe path (Google Pay rides Stripe as the gateway of record).
    implementation(libs.stripe.paymentsheet)
    implementation(libs.play.services.wallet)
}

