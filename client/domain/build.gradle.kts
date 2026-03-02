@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.dokka)
    alias(libs.plugins.kover)
}

kotlin {
    android {
        namespace = "app.logdate.client.domain"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        optimization {
            consumerKeepRules.apply {
                publish = true
                file("proguard-rules.pro")
            }
        }
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    iosArm64()
    iosSimulatorArm64()

    jvm()

    sourceSets {
        val commonTest by getting
        all {
            languageSettings.optIn("kotlin.uuid.ExperimentalUuidApi")
            compilerOptions.freeCompilerArgs.set(listOf("-Xexpect-actual-classes"))
        }
        androidMain.dependencies {
            // Android Health Connect dependencies
            implementation(libs.androidx.health.connect)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.koin.android)
        }
        commonMain.dependencies {
            // Project dependencies
            implementation(projects.shared.model)
            implementation(projects.client.repository)
            implementation(projects.client.media)
            implementation(projects.client.location)
            implementation(projects.client.intelligence)
            implementation(projects.client.networking)
            implementation(projects.client.device)
            implementation(projects.client.logdateDatastore)
            implementation(projects.client.healthConnect)
            // External dependencies
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.okio)
            implementation(libs.napier)
            // Koin
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.koin.test)
            implementation(libs.ktor.client.mock)
            implementation("com.squareup.okio:okio-fakefilesystem:3.16.4")
        }
        jvmTest.dependencies {
            implementation(libs.mockk)
        }
    }
}
