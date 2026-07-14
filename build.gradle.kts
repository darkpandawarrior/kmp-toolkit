// Plugins used by one or more modules — declared here apply-false so each module applies what it needs.
plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidKmpLibrary) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
}
