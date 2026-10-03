plugins {
    id("shared.android.library")
    `maven-publish`
}

android {
    namespace = "com.siddharth.kmp.designsystem.wallet.gms"
}

dependencies {
    api(project(":designsystem"))
    implementation(libs.koin.core)
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.pay.button.compose)
}
