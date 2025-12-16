@file:Suppress("UnstableApiUsage")

rootProject.name = "LogDate"
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
        maven {
            // Get latest version from https://androidx.dev/snapshots/builds/
            url = uri("https://androidx.dev/snapshots/builds/13551459/artifacts/repository")
        }
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
        maven {
            // Get latest version from https://androidx.dev/snapshots/builds/
            url = uri("https://androidx.dev/snapshots/builds/13551459/artifacts/repository")
        }
    }
}

// End build targets
include(":app:compose-main")
include(":app:wear")
// Core client modules
include(":client:data")
include(":client:ui")
include(":client:theme")
include(":client:domain")
include(":client:repository")
include(":client:database")
include(":client:datastore")
include(":client:networking")
include(":client:intelligence")
include(":client:location")
include(":client:sensor")
include(":client:media")
include(":client:device")
include(":client:sharing")
include(":client:permissions")
include(":client:util")
include(":client:billing")
include(":client:sync")
include(":client:health-connect")
// Client Features
include(":client:feature:core")
include(":client:feature:editor")
include(":client:feature:journal")
include(":client:feature:rewind")
include(":client:feature:timeline")
include(":client:feature:location-timeline") // TODO: Probably consolidate into timeline at some point
include(":client:feature:onboarding")
include(":client:feature:search")
// Shared cross-platform modules
include(":shared:activitypub")
include(":shared:config")
include(":shared:model")
// Server-specific modules
include(":server")
