plugins {
    id("shared.android.library")
    `maven-publish`
}

android {
    namespace = "com.siddharth.kmp.provider.upiintent"
}

dependencies {
    // PaymentGateway contract — sibling module in this monorepo.
    implementation(project(":payments-api"))
    // AppLog logging facade — sibling module in this monorepo.
    implementation(project(":common"))

    implementation(libs.core.ktx)
    // ActivityResult APIs (ActivityResultContracts) used by pay(); payments-api keeps activity as
    // `implementation`, so this module brings it in directly.
    implementation(libs.activity.compose)
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
}

