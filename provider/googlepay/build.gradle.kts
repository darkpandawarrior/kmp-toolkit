plugins {
    id("shared.android.library")
    `maven-publish`
}

android {
    namespace = "com.siddharth.kmp.provider.googlepay"
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

    implementation(libs.play.services.wallet)

    testImplementation(libs.junit)
    // org.json.JSONObject is a stub on plain JVM unit tests — Robolectric provides the real shadow.
    testImplementation(libs.robolectric)
}

