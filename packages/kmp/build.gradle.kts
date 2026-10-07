import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The StudioShare mascot: a floating companion drawn from a data pack (Brand/mascot → manifest.json +
// WebP frame atlases). This module owns the pack format, the frame player, the director that decides
// what the mascot does, and the overlay stage. It knows nothing about the app: storage, the network
// and the bundled files are injected (see MascotPlatform), so composeApp only wires it in.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    // Match :composeApp's iOS target set exactly (no iosX64).
    iosArm64()
    iosSimulatorArm64()

    // Desktop and iOS decode images through Skia; Android through BitmapFactory.
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("skia") {
                withJvm()
                withIos()
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        // Skia natives so jvmTest can render the rig to PNGs (MascotRigPreviewTest).
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}

android {
    namespace = "com.dfeverx.studioshare.mascot"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
