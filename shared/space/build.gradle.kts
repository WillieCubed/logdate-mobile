@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import app.logdate.GenerateConformanceFixtures
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlinx.serialization)
}

description = "LogDate's private and shared spaces: sealed records, and the data homes that store them."

// The sealed-record tests read the shared client conformance fixtures, so this module, the Mac app
// and the Linux app are held to the same known answers. They're embedded at build time because
// common tests run on iOS too, where there's no repository to read files from.
val generateSealedConformanceFixtures =
    tasks.register<GenerateConformanceFixtures>("generateSealedConformanceFixtures") {
        description = "Embed conformance/sealed in the test sources."
        fixtures.set(rootProject.layout.projectDirectory.dir("conformance/sealed"))
        packageName.set("app.logdate.space")
        objectName.set("SealedConformanceFixtures")
        outputDirectory.set(layout.buildDirectory.dir("generated/conformance/kotlin"))
    }

kotlin {
    explicitApi()

    android {
        namespace = "app.logdate.space"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        withHostTestBuilder {}

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    iosArm64()
    iosSimulatorArm64()

    jvm()

    sourceSets {
        commonMain.dependencies {
            api(projects.shared.lexicon)
            implementation(projects.shared.atprotoRepo)
            implementation(projects.shared.atprotoXrpc)
            implementation(libs.cryptography.provider.optimal)
            implementation(libs.ktor.client.core)
        }

        commonTest {
            kotlin.srcDir(generateSealedConformanceFixtures.flatMap { it.outputDirectory })
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}
