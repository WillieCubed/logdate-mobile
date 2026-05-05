@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinx.serialization)
    // TODO: Re-enable once we can figure out why Modifier is an unresolved reference
    alias(libs.plugins.dokka)
}

kotlin {
    android {
        namespace = "app.logdate.client.ui"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        withHostTestBuilder {}
        androidResources {
            enable = true
        }
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

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
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
            api(projects.client.theme)
            implementation(projects.client.media)
            implementation(projects.client.repository)
            implementation(projects.client.awareness)
            implementation(projects.client.util)
            implementation(projects.client.sensor)
            implementation(projects.shared.model)
            // Compose plugin dependencies
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.compose.material3)
            implementation(libs.compose.material3.adaptive.navigation.suite)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.ui.tooling.preview)
            // External dependencies
            implementation(libs.material3.adaptive)
            implementation(libs.material3.adaptive.layout)
            implementation(libs.material3.adaptive.navigation)
            implementation(libs.nav3.runtime)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.coil.compose)
            // Koin
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.kotlinx.datetime)
            // TODO: Move this to proper shared module
            implementation(libs.google.maps.compose)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.window)
            implementation(libs.multiplatform.markdown.renderer)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.ui.tooling)
}

// TODO: Possibly move assets to separate module
compose.resources {
    publicResClass = true
    generateResClass = always
    packageOfResClass = "logdate.client.ui.generated.resources"
}
