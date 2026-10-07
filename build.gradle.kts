plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.kotlinJvm) apply false
}

allprojects {
    group = "com.dfeverx.studioshare.mascot"
    version = "1.0.0"
}
