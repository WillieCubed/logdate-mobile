@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

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

    iosX64()
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
            implementation(projects.client.media)
            implementation(projects.client.repository)
            implementation(projects.shared.model)
            // Compose
            implementation(compose.runtime)
            implementation(compose.components.resources)
            // External dependencies
            implementation(libs.napier)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
    }
}

val configProperties = PropertiesLoader.loadProperties(project)

android {
    namespace = "app.logdate.client.sharing"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    // Needed to ensure manifest merger doesn't fail
    // See https://developer.android.com/build/manage-manifests#inject_build_variables_into_the_manifest
    defaultConfig {
        manifestPlaceholders["META_APP_ID"] = ""
    }

    buildFeatures {
        buildConfig = true
    }
    buildTypes {
        getByName("debug") {
            buildConfigField(
                "String",
                "META_APP_ID",
                getConfigValue("metaAppId")
            )
        }
        getByName("release") {
            isMinifyEnabled = false

            buildConfigField(
                "String",
                "META_APP_ID",
                getConfigValue("metaAppId")
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// TODO(build): Extract this to a shared build module
object PropertiesLoader {
    fun loadProperties(project: Project): Properties {
        val properties = Properties()
        val propertiesFile = project.rootProject.file("local.properties")
        if (propertiesFile.exists()) {
            properties.load(propertiesFile.inputStream())
        }
        return properties
    }
}

fun getConfigValue(key: String): String {
    return "\"${configProperties.getProperty(key)}\""
}