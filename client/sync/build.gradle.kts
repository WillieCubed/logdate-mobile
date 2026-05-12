@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.dokka)
}

kotlin {
    android {
        namespace = "app.logdate.client.sync"
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

    jvm("desktop")

    sourceSets {
        val desktopMain by getting

        all {
            languageSettings.optIn("kotlin.uuid.ExperimentalUuidApi")
            compilerOptions.freeCompilerArgs.set(listOf("-Xexpect-actual-classes"))
        }
        commonMain.dependencies {
            // Project dependencies
            implementation(projects.shared.model)
            implementation(projects.shared.config)
            implementation(projects.client.networking)
            implementation(projects.client.repository)
            implementation(projects.client.database)
            implementation(projects.client.logdateDatastore)
            implementation(projects.client.device)
            implementation(projects.client.media)
            implementation(projects.client.util)

            // External dependencies
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.cryptography.provider.optimal)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.napier)

            // Ktor
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.serialization)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.logging)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
            implementation(projects.client.logdateDatastore)
            implementation(projects.client.util)
        }
        androidMain.dependencies {
            implementation(projects.client.database)
            implementation(projects.client.notifications)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.koin.android)
            implementation(libs.koin.androidx.workmanager)
            implementation(libs.androidx.work.runtime)
            // Room for transaction support
            implementation(libs.room.runtime)
            // Firebase
            implementation(libs.kotlinx.coroutines.play.services)
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.firestore)
        }
        desktopMain.dependencies {
            implementation(projects.client.database)
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}
