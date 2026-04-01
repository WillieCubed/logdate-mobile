import com.android.build.api.dsl.ApplicationExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.screenshot)
}

val baselineProfileRequested =
    gradle.startParameter.taskNames.any { taskName ->
        taskName.contains("BaselineProfile", ignoreCase = true)
    }

extensions.configure<ApplicationExtension> {
    namespace = "app.logdate.wear"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.logdate.wear"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (baselineProfileRequested) {
                signingConfig = signingConfigs.getByName("debug")
                isProfileable = true
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        create("benchmark") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            signingConfig = signingConfigs.getByName("debug")
            isProfileable = true
        }
    }
    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // Enable core library desugaring for health-connect
        isCoreLibraryDesugaringEnabled = true
    }
    experimentalProperties["android.experimental.enableScreenshotTest"] = true
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        freeCompilerArgs.add("-opt-in=kotlin.uuid.ExperimentalUuidApi")
    }
}

dependencies {
    // Core library desugaring for health-connect
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":benchmark:wear-baselineprofile"))

    // Koin dependency injection
    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)

    // Napier logging
    implementation(libs.napier)

    // Kotlinx coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // Client dependencies for Wear - shared data infrastructure
    implementation(projects.client.repository)
    implementation(projects.client.domain)
    implementation(projects.client.data)
    implementation(projects.client.database)
    implementation(projects.client.device)
    implementation(projects.client.logdateDatastore)
    implementation(projects.client.sync)
    implementation(projects.shared.config)
    implementation(projects.shared.model)
    implementation(projects.client.media)

    // Serialization (required by LocalFirstDraftRepository)
    implementation(libs.kotlinx.serialization.json)

    // Add navigation for Wear
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.compose.material.iconsExtended)

    // Additional Compose support
    implementation(libs.kotlinx.datetime)
    implementation(libs.koin.compose.viewmodel)

    implementation(libs.play.services.wearable)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)

    // Material 3 for Wear OS
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.wear.material3)
    implementation(libs.androidx.wear.foundation)
    implementation(libs.androidx.wear.navigation)
    implementation(libs.material)

    implementation(libs.androidx.wear.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.tiles)
    implementation(libs.androidx.tiles.material)
    implementation(libs.androidx.tiles.tooling.preview)
    implementation(libs.horologist.compose.tools)
    implementation(libs.horologist.tiles)
    implementation(libs.androidx.watchface.complications.data.source.ktx)
    implementation(libs.androidx.health.services.client)

    // Media3 ExoPlayer for voice note playback (reuses AudioPlaybackService from client:media)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.common)
    implementation(libs.media3.session)

    // Unit test dependencies
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.mockk)

    // Screenshot test dependencies
    screenshotTestImplementation(libs.screenshot.validation.api)

    // Instrumented test dependencies
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlin.test.junit)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    debugImplementation(libs.androidx.tiles.tooling)
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
