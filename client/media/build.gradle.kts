import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.dokka)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    android {
        namespace = "app.logdate.client.media"
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
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.core)
            // Koin
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            // Compose
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            // Logging
            implementation(libs.napier)
            // Repository
            implementation(projects.client.repository)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
            implementation(projects.client.networking)
            implementation(projects.client.notifications)
            implementation(libs.androidx.work.runtime)
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.common)
            implementation(libs.media3.session)
            implementation(libs.play.feature.delivery.ktx)
            implementation(libs.kotlinx.coroutines.play.services)
        }
        desktopMain.dependencies {
            // Sherpa-ONNX JVM bindings (Java classes wrapping the JNI surface).
            // The native libs needed for runtime live in the per-platform
            // sherpa-onnx-native-lib-* jars below — Sherpa's LibraryUtils picks
            // up whichever one matches the host OS at first use.
            implementation(files("${rootProject.projectDir}/libs/sherpa-onnx-v1.12.35.jar"))
            implementation(files("${rootProject.projectDir}/libs/sherpa-onnx-native-lib-osx-aarch64-v1.12.35.jar"))
            // Runtime tar.bz2 unpacking for the on-demand model downloads,
            // mirroring the dependency the speechrecognition dynamic feature
            // module already pulls in on Android.
            implementation(libs.commons.compress)
        }
        findByName("androidHostTest")?.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlin.test.junit)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
