rootProject.name = "studioshare-mascot"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// 1. Core Compose Multiplatform library (pack, sprite, rig, director, stage)
include(":mascot-core")
project(":mascot-core").projectDir = file("packages/kmp")

// 2. Desktop Background Companion Agent (tray, mini-HUD, native OS notifications)
include(":mascot-agent")
project(":mascot-agent").projectDir = file("packages/agent")

// 3. Standalone Desktop interactive runner / testbench
include(":mascot-preview")
project(":mascot-preview").projectDir = file("apps/desktop-preview")
