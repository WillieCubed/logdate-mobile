@file:Suppress("UnstableApiUsage")

import com.android.build.api.dsl.ApplicationExtension

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.gradlePlayPublisher)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.crashlytics)
    alias(libs.plugins.screenshot)
}

val baselineProfileRequested =
    gradle.startParameter.taskNames.any { taskName ->
        taskName.contains("BaselineProfile", ignoreCase = true)
    }
val androidTestClassOverride = providers.gradleProperty("logdate.androidTestClass").orNull
val androidTestPackageOverride = providers.gradleProperty("logdate.androidTestPackage").orNull

/**
 * Production release versionCode comes from CI. Priority order:
 *
 *  1. `LOGDATE_VERSION_CODE` env var (CI sets this from the commit graph plus a
 *     small commit-hash-derived provenance fragment).
 *  2. `logdate.versionCode` Gradle property (for ad-hoc local release builds).
 *  3. Fallback `1` — the historical hard-coded value, kept so `assembleDebug` still works
 *     without extra configuration.
 *
 * Play Store requires monotonically-increasing versionCodes per upload, so keeping this in CI
 * rather than hard-coding prevents collisions while keeping release provenance tied to a commit.
 */
val resolvedVersionCode: Int =
    (
        System.getenv("LOGDATE_VERSION_CODE")
            ?: providers.gradleProperty("logdate.versionCode").orNull
    )?.toIntOrNull() ?: 1

val resolvedVersionName: String =
    System.getenv("LOGDATE_VERSION_NAME")
        ?: providers.gradleProperty("logdate.versionName").orNull
        ?: "0.1.0"
val resolvedPlayTrack: String =
    System.getenv("LOGDATE_PLAY_TRACK")
        ?: providers.gradleProperty("logdate.play.track").orNull
        ?: "internal"

/**
 * Release signing material. Set these via env (CI/secrets) or `~/.gradle/gradle.properties`:
 *
 *  - `LOGDATE_RELEASE_STORE_FILE` (absolute or project-relative path to the .jks)
 *  - `LOGDATE_RELEASE_STORE_PASSWORD`
 *  - `LOGDATE_RELEASE_KEY_ALIAS`
 *  - `LOGDATE_RELEASE_KEY_PASSWORD`
 *
 * When any of these are missing, the release build falls back to the debug signing config so
 * local assembles don't fail on developer machines. CI *must* set all four to produce a
 * Play-publishable APK/AAB.
 */
fun resolveRelease(prop: String): String? =
    System.getenv("LOGDATE_RELEASE_${prop.uppercase()}")
        ?: providers.gradleProperty("logdate.release.$prop").orNull

val releaseStoreFile: String? = resolveRelease("storeFile")
val releaseStorePassword: String? = resolveRelease("storePassword")
val releaseKeyAlias: String? = resolveRelease("keyAlias")
val releaseKeyPassword: String? = resolveRelease("keyPassword")
val hasReleaseSigningConfig: Boolean =
    !releaseStoreFile.isNullOrBlank() &&
        !releaseStorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank()

if (baselineProfileRequested) {
    apply(
        plugin =
            libs.plugins.androidx.baselineprofile
                .get()
                .pluginId,
    )
}

extensions.configure<ApplicationExtension> {
    namespace = "app.logdate.client"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    dynamicFeatures +=
        setOf(
            ":client:feature:remotedisplay",
            ":client:feature:speechrecognition",
        )
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
        versionCode = resolvedVersionCode
        versionName = resolvedVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        if (androidTestClassOverride != null) {
            testInstrumentationRunnerArguments["class"] = androidTestClassOverride
        }
        if (androidTestPackageOverride != null) {
            testInstrumentationRunnerArguments["package"] = androidTestPackageOverride
        }
    }

    if (hasReleaseSigningConfig) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                // enable both V1 and V2 signatures so Play accepts the upload
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        getByName("debug") {
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
        }
        getByName("release") {
            isMinifyEnabled = true
            isDebuggable = false
            signingConfig =
                if (baselineProfileRequested || !hasReleaseSigningConfig) {
                    signingConfigs.getByName("debug")
                } else {
                    signingConfigs.getByName("release")
                }
            if (baselineProfileRequested) {
                isProfileable = true
            }
        }
        create("benchmark") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            isProfileable = true
        }
    }

    androidResources {
        noCompress += listOf("cvr")
    }

    packaging {
        jniLibs {
            keepDebugSymbols += "**/libandroidx.graphics.path.so"
            keepDebugSymbols += "**/libdatastore_shared_counter.so"
            keepDebugSymbols += "**/libimage_processing_util_jni.so"
            keepDebugSymbols += "**/libmockkjvmtiagent.so"
            keepDebugSymbols += "**/libsqlcipher.so"
            keepDebugSymbols += "**/libsqliteJni.so"
            keepDebugSymbols += "**/libsurface_util_jni.so"
        }
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
        animationsDisabled = true
        execution = "ANDROID_TEST_ORCHESTRATOR"
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

tasks.configureEach {
    if (name.startsWith("mergeExtDex") && name.endsWith("AndroidTest")) {
        val variantName =
            name
                .removePrefix("mergeExtDex")
                .removeSuffix("AndroidTest")
        dependsOn("desugar${variantName}AndroidTestFileDependencies")
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

play {
    track.set(resolvedPlayTrack)
    defaultToAppBundles.set(true)
}

dependencies {
    implementation(projects.app.composeMain)
    implementation(projects.client.location)
    implementation(libs.androidx.profileinstaller)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)
    if (baselineProfileRequested) {
        add("baselineProfile", project(":benchmark:phone-baselineprofile"))
    }
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
    testImplementation(libs.play.services.wearable)
    testImplementation(projects.client.feature.core)
    testImplementation(projects.client.database)
    testImplementation(projects.client.domain)
    testImplementation(projects.client.healthConnect)
    testImplementation(projects.client.repository)
    testImplementation(projects.client.sync)
    testImplementation(projects.shared.model)

    androidTestImplementation(projects.app.composeMain)
    androidTestImplementation(projects.client.feature.core)
    androidTestImplementation(projects.client.feature.editor)
    androidTestImplementation(projects.client.feature.journal)
    androidTestImplementation(projects.client.feature.onboarding)
    androidTestImplementation(projects.client.domain)
    androidTestImplementation(projects.client.healthConnect)
    androidTestImplementation(projects.client.intelligence)
    androidTestImplementation(projects.client.location)
    androidTestImplementation(projects.client.media)
    androidTestImplementation(projects.client.networking)
    androidTestImplementation(projects.client.notifications)
    androidTestImplementation(projects.client.permissions)
    androidTestImplementation(projects.client.repository)
    androidTestImplementation(projects.client.database)
    androidTestImplementation(projects.client.data)
    androidTestImplementation(projects.client.logdateDatastore)
    androidTestImplementation(projects.client.sharing)
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
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation("io.mockk:mockk-android:1.14.9")
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestUtil(libs.androidx.test.orchestrator)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.activity.compose)
    androidTestImplementation(libs.compose.material3)
    androidTestImplementation(libs.napier)
    androidTestImplementation(project.dependencies.platform(libs.koin.bom))
    androidTestImplementation(libs.koin.core)
    androidTestImplementation(libs.koin.android)
    androidTestImplementation(libs.koin.androidx.workmanager)
    androidTestImplementation(libs.androidx.work.runtime)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.androidx.glance.appwidget)
    androidTestImplementation(projects.client.feature.androidWidgets)
    androidTestImplementation(libs.koin.test)
    androidTestImplementation(projects.client.feature.library)
    androidTestImplementation(projects.client.feature.search)
    androidTestImplementation(projects.client.theme)
    androidTestImplementation(projects.client.ui)
    androidTestImplementation(libs.compose.material.icons.extended)

    screenshotTestImplementation(libs.androidx.ui.tooling)
    screenshotTestImplementation(libs.screenshot.validation.api)
    screenshotTestImplementation(libs.compose.material3)
    screenshotTestImplementation(libs.compose.material.icons.extended)
    screenshotTestImplementation(libs.compose.components.resources)
    screenshotTestImplementation(libs.compose.runtime)
    screenshotTestImplementation(libs.compose.foundation)
    screenshotTestImplementation(projects.client.awareness)
    screenshotTestImplementation(projects.client.feature.editor)
    screenshotTestImplementation(projects.client.screenshotScenes)
    screenshotTestImplementation(projects.client.feature.core)
    screenshotTestImplementation(projects.client.feature.onboarding)
    screenshotTestImplementation(projects.client.feature.journal)
    screenshotTestImplementation(projects.client.feature.rewind)
    screenshotTestImplementation(projects.client.feature.timeline)
    screenshotTestImplementation(projects.client.feature.locationTimeline)
    screenshotTestImplementation(projects.client.feature.library)
    screenshotTestImplementation(projects.client.feature.search)
    screenshotTestImplementation(projects.client.permissions)
    screenshotTestImplementation(projects.client.domain)
    screenshotTestImplementation(projects.client.data)
    screenshotTestImplementation(projects.client.database)
    screenshotTestImplementation(projects.client.media)
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

afterEvaluate {
    tasks.matching { it.name.startsWith("uninstall") }.configureEach {
        enabled = false
        doFirst {
            throw GradleException(
                "Uninstall tasks are disabled to protect app data on connected devices. " +
                    "Use 'installDebug' to upgrade in place.",
            )
        }
    }
}
