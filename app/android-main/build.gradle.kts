@file:Suppress("UnstableApiUsage")

import com.android.build.api.dsl.ApplicationExtension
import java.util.Properties

val configProperties =
    Properties().apply {
        val propertiesFile = rootProject.file("local.properties")
        if (propertiesFile.exists()) {
            propertiesFile.inputStream().use(::load)
        }
    }

val googleMapsApiKey =
    providers
        .environmentVariable("GOOGLE_MAPS_API_KEY")
        .orElse(providers.gradleProperty("GOOGLE_MAPS_API_KEY"))
        .orElse(configProperties.getProperty("GOOGLE_MAPS_API_KEY") ?: "")
        .get()

val googleMapsManifestValue =
    if (googleMapsApiKey.isBlank()) {
        "@string/google_api_key"
    } else {
        googleMapsApiKey
    }

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
        manifestPlaceholders["googleMapsApiKey"] = googleMapsManifestValue
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

tasks.withType<Test>().configureEach {
    maxHeapSize =
        if (name.contains("Screenshot", ignoreCase = true)) {
            "12g"
        } else {
            "4g"
        }
}

configurations.all {
    resolutionStrategy.eachDependency {
        // Navigation3 beta01 pulls compose.ui to 1.11.0-beta01 while JetBrains
        // Compose 1.11.0-alpha04 maps foundation to 1.11.0-alpha06. The ABI
        // changed between alpha and beta for PointerEventType.Pan, causing a
        // runtime NoSuchMethodError. Force all androidx.compose artifacts to the
        // same beta01 version so foundation and ui stay compatible.
        if (requested.group.startsWith("androidx.compose") &&
            requested.version?.contains("1.11.0-alpha") == true
        ) {
            useVersion("1.11.0-beta01")
        }
    }
}

dependencies {
    implementation(projects.app.composeMain)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.work.runtime)

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

    androidTestImplementation(projects.app.composeMain)
    androidTestImplementation(projects.client.feature.core)
    androidTestImplementation(projects.client.feature.editor)
    androidTestImplementation(projects.client.feature.journal)
    androidTestImplementation(projects.client.domain)
    androidTestImplementation(projects.client.media)
    androidTestImplementation(projects.client.permissions)
    androidTestImplementation(projects.client.repository)
    androidTestImplementation(projects.client.database)
    androidTestImplementation(projects.client.data)
    androidTestImplementation(projects.client.sync)
    androidTestImplementation(projects.client.device)
    androidTestImplementation(projects.shared.model)
    androidTestImplementation(libs.kotlin.test.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.kotlinx.datetime)
    androidTestImplementation(libs.room.runtime)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.activity.compose)
    androidTestImplementation(libs.compose.material3)
    androidTestImplementation(libs.napier)
    androidTestImplementation(project.dependencies.platform(libs.koin.bom))
    androidTestImplementation(libs.koin.core)
    androidTestImplementation(libs.koin.android)
    androidTestImplementation(libs.koin.test)

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
    screenshotTestImplementation(projects.client.data)
    screenshotTestImplementation(projects.client.database)
    screenshotTestImplementation(projects.client.sync)
    screenshotTestImplementation(projects.client.location)
    screenshotTestImplementation(projects.client.repository)
    screenshotTestImplementation(projects.client.ui)
    screenshotTestImplementation(projects.client.billing)
    screenshotTestImplementation(projects.client.theme)
    screenshotTestImplementation(projects.shared.model)
    screenshotTestImplementation(libs.kotlinx.datetime)
    screenshotTestImplementation(libs.androidx.navigation3.runtime)
}
