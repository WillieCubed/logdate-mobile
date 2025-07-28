@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.dokka)
    alias(libs.plugins.kover)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    jvm()

    sourceSets {
        all {
            languageSettings.optIn("kotlin.uuid.ExperimentalUuidApi")
            compilerOptions.freeCompilerArgs.set(listOf("-Xexpect-actual-classes"))
        }
        commonMain.dependencies {
            // Project dependencies
            implementation(projects.shared.model)
            implementation(projects.client.database)
            implementation(projects.client.datastore)
            implementation(projects.client.repository)
            implementation(projects.client.device)
            // External dependencies
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.coroutines.core)
            // Koin
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            // Logging
            implementation(libs.napier)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.kotlinx.coroutines.play.services)
            implementation(libs.play.services.location)
            implementation(libs.play.services.maps)
            implementation(libs.google.maps.places)
            implementation(libs.koin.android)
            implementation(libs.androidx.work.runtime)
            implementation(libs.koin.androidx.workmanager)
        }
    }
}

android {
    namespace = "app.logdate.client.location"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    lint {
        abortOnError = false
        warningsAsErrors = false
        // Ignore resource errors and API level warnings for now
        disable += listOf("NewApi", "ObsoleteSdkInt", "MissingTranslation", "ResourceType")
    }
}