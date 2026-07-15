plugins {
    id("shared.android.library")
    `maven-publish`
}

android {
    namespace = "com.siddharth.kmp.provider.omise"
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

    // Omise Android SDK — on Maven Central (unlike Square). 5.6.0 is the latest stable published
    // version. Excludes the old kotlin-android-extensions-runtime transitive dep, which duplicates
    // classes already provided by a modern kotlin-parcelize-runtime.
    implementation("co.omise:omise-android:5.6.0") {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-android-extensions-runtime")
    }

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

