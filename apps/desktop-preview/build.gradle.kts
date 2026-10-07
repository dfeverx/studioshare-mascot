import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":mascot-core"))
    implementation(project(":mascot-agent"))
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.coroutines.core)
}

compose.desktop {
    application {
        mainClass = "com.dfeverx.studioshare.mascot.preview.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "MascotStudio"
            packageVersion = "1.0.0"
        }
    }
}
