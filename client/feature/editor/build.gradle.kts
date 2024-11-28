@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.dokka)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    )

    jvm("desktop")

    sourceSets {
        all {
            languageSettings.optIn("kotlin.uuid.ExperimentalUuidApi")
            compilerOptions.freeCompilerArgs.set(listOf("-Xexpect-actual-classes"))
        }

        val desktopMain by getting

        commonMain.dependencies {
            // Project dependencies
            implementation(projects.client.ui)
            implementation(projects.client.domain)
            implementation(projects.client.repository)
            implementation(projects.client.util)
            implementation(projects.client.media)
            implementation(projects.client.location)
            implementation(projects.shared.model)
            // Compose plugin dependencies
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.materialIconsExtended)
            implementation(compose.material3)
            implementation(compose.material3AdaptiveNavigationSuite)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            // Core dependencies
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.androidx.navigation.compose)
            // External dependencies
            // Koin
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            // Others
            implementation(libs.coil.compose)
        }

        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.camera.camera2)
            implementation(libs.androidx.camera.lifecycle)
            implementation(libs.androidx.camera.video)
            implementation(libs.androidx.camera.view)
            implementation(libs.androidx.camera.extensions)
            implementation(libs.accompanist.permissions)
        }
    }
}
//dependencies {
//    implementation(project(":core:data"))
//    implementation(project(":core:ui"))
//    implementation(project(":core:model"))
//    implementation(project(":core:util"))
//    implementation(project(":core:world"))
//    implementation(project(":core:media"))
//
//    // Compose in logdate.compose build logic
//    implementation(libs.androidx.navigation.compose)
//    implementation(libs.androidx.hilt.navigation.compose)
////    implementation(libs.androidx.compose.material3.carousel)
////    implementation(libs.androidx.compose.material3)
//    // TODO: Figure out what dependency is causing this hell
//    implementation("com.google.j2objc:j2objc-annotations:3.0.0")
////    api(libs.guava)
//}


dependencies {
    debugImplementation(compose.uiTooling)
}

android {
    namespace = "app.logdate.client.feature.editor"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}