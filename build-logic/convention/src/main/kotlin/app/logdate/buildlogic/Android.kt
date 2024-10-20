package app.logdate.buildlogic

import com.android.build.api.dsl.CommonExtension
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import dagger.hilt.android.plugin.HiltExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/**
 * Configures the Android app module.
 *
 * This sets the target SDK to 34 and configures the build types. The release build type is configured
 * to enable minification and shrink resources.
 */
internal fun configureAndroidApp(commonExtension: BaseAppModuleExtension) {
    commonExtension.apply {
        defaultConfig {
            targetSdk = 35
        }

        buildTypes {
            getByName("release") {
                isJniDebuggable = false
                isMinifyEnabled = true
                isShrinkResources = true
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
                )
            }
        }
    }
}

internal fun Project.configureAndroid(commonExtension: CommonExtension<*, *, *, *, *, *>) {
    commonExtension.apply {
        namespace = "co.reasonabletech.logdate"

        compileSdk = 35

        defaultConfig {
            minSdk = 29
            testInstrumentationRunner = "app.logdate.mobile.testing.HiltTestRunner"
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_21
            targetCompatibility = JavaVersion.VERSION_21
        }

        packaging {
            resources {
                excludes += "/META-INF/{AL2.0,LGPL2.1}"
            }
        }

    }
    configureKotlin()
}

/**
 * Configures the Hilt plugin for the project.
 */
internal fun Project.configureHilt() {
    extensions.configure<HiltExtension> {
        enableAggregatingTask = true
    }
}

/**
 * Configure base Kotlin options for JVM (non-Android)
 */
internal fun Project.configureKotlinJvm() {
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    configureKotlin()
}

/**
 * Configure base Kotlin options
 */
private fun Project.configureKotlin() {
    // Use withType to workaround https://youtrack.jetbrains.com/issue/KT-55947
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            val treatWarningsAsErrors: String? by project
            // Treat all Kotlin warnings as errors (disabled by default)
            // Override by setting warningsAsErrors=true in your ~/.gradle/gradle.properties
            allWarningsAsErrors.set(treatWarningsAsErrors.toBoolean())
        }
    }
}
