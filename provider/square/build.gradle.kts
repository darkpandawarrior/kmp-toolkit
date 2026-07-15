plugins {
    id("shared.android.library")
    `maven-publish`
}

android {
    namespace = "com.siddharth.kmp.provider.square"
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

    // In-App Payments Card Entry SDK (sdk.squareup.com/public/android — not on Maven Central).
    // card-entry's own published pom omits nonce-api as a transitive dependency even though
    // CardEntry's API surface (Callback/Card/nonce) requires it at compile time — added explicitly.
    implementation("com.squareup.sdk.in-app-payments:card-entry:1.6.8")
    implementation("com.squareup.sdk.in-app-payments:nonce-api:1.6.8")

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

