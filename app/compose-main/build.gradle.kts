@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.dokka)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.screenshot)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        // Configure screenshot tests to use the test source set tree
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        instrumentedTestVariant.sourceSetTree.set(org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree.test)
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "composeApp"
            isStatic = true
        }
    }

    jvm("desktop")

    sourceSets {
        val desktopMain by getting

        all {
            languageSettings.optIn("kotlin.uuid.ExperimentalUuidApi")
            compilerOptions.freeCompilerArgs.set(listOf("-Xexpect-actual-classes"))
        }
        commonMain.dependencies {
            // Project dependencies
            implementation(projects.client.feature.core)
            implementation(projects.client.feature.onboarding)
            implementation(projects.client.feature.editor)
            implementation(projects.client.feature.timeline)
            implementation(projects.client.feature.journal)
            implementation(projects.client.feature.rewind)
            implementation(projects.client.feature.locationTimeline)
            implementation(projects.client.feature.search)
            implementation(projects.client.data)
            implementation(projects.client.ui)
            implementation(projects.client.theme)
            implementation(projects.client.networking) // TODO: See if this can be hoisted down
            implementation(projects.client.device)
            implementation(projects.client.intelligence)
            implementation(projects.client.domain)
            implementation(projects.client.location)
            implementation(projects.client.sync)
            implementation(projects.client.healthConnect)
            implementation(projects.client.util)
            // External dependencies
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.material3AdaptiveNavigationSuite)
            implementation(compose.ui)
            implementation(compose.animation)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.datetime)
            // Koin
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)

            implementation(libs.napier)
            implementation(libs.filekit.compose)
        }
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.splashscreen)
            implementation(libs.androidx.lifecycle.viewmodel.navigation3)
            implementation(libs.androidx.material3.adaptive.navigation3)
            implementation(libs.androidx.navigation3.ui)
            implementation(libs.androidx.navigation3.runtime)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.koin.android)
            implementation(libs.koin.androidx.workmanager)
            implementation(libs.material)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}

android {
    namespace = "app.logdate.client"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        // TODO: Change to app.logdate
        applicationId = "co.reasonabletech.logdate"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // Exclude duplicate classes from different libraries
            pickFirsts += "**/*.properties"
            pickFirsts += "META-INF/DEPENDENCIES"
            pickFirsts += "META-INF/LICENSE"
            pickFirsts += "META-INF/LICENSE.txt"
            pickFirsts += "META-INF/LICENSE.md"
            pickFirsts += "META-INF/NOTICE"
            pickFirsts += "META-INF/NOTICE.txt"
            pickFirsts += "META-INF/INDEX.LIST"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isDebuggable = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Enable core library desugaring for health-connect
        isCoreLibraryDesugaringEnabled = true
    }
    experimentalProperties["android.experimental.enableScreenshotTest"] = true
}

dependencies {
    debugImplementation(compose.uiTooling)
    // Add core library desugaring for Java 8+ APIs support on lower Android versions
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    // Screenshot testing
    screenshotTestImplementation(libs.androidx.ui.tooling)
    screenshotTestImplementation(libs.screenshot.validation.api)
    screenshotTestImplementation(compose.material3)
    screenshotTestImplementation(compose.runtime)
    screenshotTestImplementation(compose.foundation)
}

// Workaround for Google Compose Screenshot Testing + KMP compatibility issue.
// The screenshot plugin doesn't properly set moduleName for KMP projects, causing
// "Required value was null" errors during compilation. Explicitly setting the module
// name resolves this. See: https://issuetracker.google.com/issues/402137754
tasks.withType<KotlinCompile>().configureEach {
    if (name.contains("ScreenshotTest", ignoreCase = true)) {
        compilerOptions {
            moduleName.set("compose-main-screenshottest")
        }
    }
}

compose.desktop {
    application {
        mainClass = "app.logdate.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "LogDate"
            description = "The LogDate desktop application"
            copyright = "© 2024 Willie Chalmers III"
            packageVersion = "1.0.0"

            windows {
                console = true
                perUserInstall = true
                iconFile.set(project.file("app/compose-main/src/commonMain/composeResources/drawable/ic_launcher_google_play.png"))
            }

            macOS {
                bundleID = "app.logdate"
                dockName = "LogDate"
                appCategory = "public.app-category.lifestyle"
                iconFile.set(project.file("app/compose-main/src/commonMain/composeResources/drawable/ic_launcher_google_play.png"))
            }

            linux {
                debMaintainer = "contact@logdate.app"
                iconFile.set(project.file("app/compose-main/src/commonMain/composeResources/drawable/ic_launcher_google_play.png"))

                modules("jdk.security.auth") // Needed for FileKit
            }
        }
    }
}