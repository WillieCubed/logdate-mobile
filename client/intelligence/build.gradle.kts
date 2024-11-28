@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
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

    jvm()

    sourceSets {
        all {
            languageSettings.optIn("kotlin.uuid.ExperimentalUuidApi")
            compilerOptions.freeCompilerArgs.set(listOf("-Xexpect-actual-classes"))
        }
        commonMain.dependencies {
            // Project dependencies
            implementation(projects.shared.model)
            implementation(projects.client.networking)
            // External dependencies
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.coroutines.core)
            // Koin
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)

            implementation(libs.napier)
        }
    }
}

val configProperties = PropertiesLoader.loadProperties(project)

android {
    // TODO: See if all module namespaces must be unique
    namespace = "app.logdate.client.intelligence"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    // Needed to ensure manifest merger doesn't fail
    // See https://developer.android.com/build/manage-manifests#inject_build_variables_into_the_manifest
    defaultConfig {
        manifestPlaceholders["OPENAI_API_KEY"] = ""
    }

    buildFeatures {
        buildConfig = true
    }
    buildTypes {
        getByName("debug") {
            buildConfigField(
                "String",
                "OPENAI_API_KEY",
                getConfigValue("apiKeys.openAiApiKey")
            )
        }
        getByName("release") {
            isMinifyEnabled = false

            buildConfigField(
                "String",
                "OPENAI_API_KEY",
                getConfigValue("apiKeys.openAiApiKey")
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