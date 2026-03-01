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
        namespace = "app.logdate.client.data"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
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
            implementation(projects.client.domain)
            implementation(projects.client.repository)
            implementation(projects.client.database)
            implementation(projects.client.logdateDatastore)
            implementation(projects.client.device)
            implementation(projects.client.networking)
            implementation(projects.client.permissions)
            implementation(projects.client.sync)
            implementation(projects.client.util)
            implementation(projects.client.media)
            // External dependencies
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.androidx.datastore.preferences)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.napier)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.koin.android)
            // Firebase
            implementation(libs.kotlinx.coroutines.play.services)
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.firestore)
        }
        desktopMain.dependencies {
            implementation(libs.kotlinx.coroutines.swing)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.koin.test)
        }
    }
}
