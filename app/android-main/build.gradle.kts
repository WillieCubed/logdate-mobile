@file:Suppress("UnstableApiUsage")

import com.android.build.api.dsl.ApplicationExtension

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.screenshot)
}

extensions.configure<ApplicationExtension> {
    namespace = "app.logdate.client"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "co.reasonabletech.logdate"
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.android.targetSdk
                .get()
                .toInt()
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isDebuggable = false
        }
    }

    androidResources {
        noCompress += listOf("cvr")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
    }

    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    testOptions {
        unitTests.all {
            it.maxHeapSize = "4g"
        }
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.uuid.ExperimentalUuidApi")
    }
}

tasks.withType<Test> {
    maxHeapSize = "4g"
}

dependencies {
    implementation(projects.app.composeMain)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.datetime)
    testImplementation(libs.mockk)
    testImplementation(libs.compose.runtime)
    testImplementation(libs.androidx.navigation3.runtime)
    testImplementation(libs.androidx.health.connect)
    testImplementation(projects.client.domain)
    testImplementation(projects.client.healthConnect)

    androidTestImplementation(projects.client.permissions)
    androidTestImplementation(projects.client.repository)
    androidTestImplementation(libs.kotlin.test.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.play.app.update.testing)

    screenshotTestImplementation(libs.androidx.ui.tooling)
    screenshotTestImplementation(libs.screenshot.validation.api)
    screenshotTestImplementation(libs.compose.material3)
    screenshotTestImplementation(libs.compose.material.icons.extended)
    screenshotTestImplementation(libs.compose.components.resources)
    screenshotTestImplementation(libs.compose.runtime)
    screenshotTestImplementation(libs.compose.foundation)
    screenshotTestImplementation(projects.client.feature.editor)
    screenshotTestImplementation(projects.client.feature.core)
    screenshotTestImplementation(projects.client.feature.onboarding)
    screenshotTestImplementation(projects.client.feature.journal)
    screenshotTestImplementation(projects.client.feature.rewind)
    screenshotTestImplementation(projects.client.feature.timeline)
    screenshotTestImplementation(projects.client.feature.locationTimeline)
    screenshotTestImplementation(projects.client.feature.search)
    screenshotTestImplementation(projects.client.permissions)
    screenshotTestImplementation(projects.client.domain)
    screenshotTestImplementation(projects.client.location)
    screenshotTestImplementation(projects.client.repository)
    screenshotTestImplementation(projects.client.ui)
    screenshotTestImplementation(projects.client.billing)
    screenshotTestImplementation(projects.client.theme)
    screenshotTestImplementation(projects.shared.model)
    screenshotTestImplementation(libs.kotlinx.datetime)
}
