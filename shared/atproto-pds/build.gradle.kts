@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.gradle.api.publish.maven.MavenPublication
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.dokka)
    alias(libs.plugins.kover)
    `maven-publish`
    signing
}

group = "studio.hypertext.atproto"
version = "0.1.0"

kotlin {
    explicitApi()

    android {
        namespace = "studio.hypertext.atproto.pds"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    iosArm64()
    iosSimulatorArm64()

    jvm()

    sourceSets {
        commonMain.dependencies {
            api(projects.shared.atprotoIdentity)
            api(projects.shared.atprotoRepo)
            api(projects.shared.atprotoSyntax)
            api(projects.shared.atprotoXrpc)
            api(libs.kotlinx.serialization.core)
            api(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

publishing {
    publications.withType(MavenPublication::class.java).configureEach {
        pom {
            name.set("atproto-pds")
            description.set("Kotlin Multiplatform AT Protocol PDS contracts and wire models.")
            url.set("https://github.com/TheHypertextStudio/logdate-android")
            licenses {
                license {
                    name.set("Apache-2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }
            scm {
                url.set("https://github.com/TheHypertextStudio/logdate-android")
                connection.set("scm:git:https://github.com/TheHypertextStudio/logdate-android.git")
                developerConnection.set("scm:git:ssh://git@github.com/TheHypertextStudio/logdate-android.git")
            }
        }
    }
}

signing {
    isRequired = false
}
