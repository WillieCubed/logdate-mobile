@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.dokka)
    alias(libs.plugins.buildConfig)
}

kotlin {
    android {
        namespace = "app.logdate.client.sharing"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        androidResources {
            enable = true
        }

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
            implementation(projects.client.media)
            implementation(projects.client.repository)
            implementation(projects.shared.model)
            // Compose
            implementation(libs.compose.runtime)
            implementation(libs.compose.components.resources)
            // External dependencies
            implementation(libs.napier)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
            implementation(libs.zxing.core)
        }
    }
}

val configProperties = PropertiesLoader.loadProperties(project)

buildConfig {
    packageName("app.logdate.client.sharing")
    buildConfigField("META_APP_ID", configProperties.getProperty("logdate.metaAppId") ?: "")
}

val generateFacebookResources by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/res/facebook")
    val appId = configProperties.getProperty("logdate.metaAppId") ?: ""
    outputs.dir(outputDir)
    doLast {
        val valuesDir = outputDir.get().asFile.resolve("values")
        valuesDir.mkdirs()
        valuesDir.resolve("facebook.xml").writeText(
            """
            |<?xml version="1.0" encoding="utf-8"?>
            |<resources>
            |    <string name="facebook_app_id" translatable="false">$appId</string>
            |</resources>
            """.trimMargin(),
        )
    }
}

extensions.getByType(com.android.build.api.variant.AndroidComponentsExtension::class.java).onVariants { variant ->
    variant.sources.res?.addStaticSourceDirectory(
        layout.buildDirectory
            .dir("generated/res/facebook")
            .get()
            .asFile.absolutePath,
    )
}

tasks
    .matching { it.name.contains("AndroidMainResources") || it.name.contains("AndroidMainRFile") }
    .configureEach { dependsOn(generateFacebookResources) }

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

fun getConfigValue(key: String): String = "\"${configProperties.getProperty(key)}\""
