package app.logdate.buildlogic

import com.android.build.api.dsl.CommonExtension
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
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
            targetSdk = 34
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

/**
 * Loads local build properties from the local.properties file and adds them to the build config.
 */
internal fun Project.configureBuildConfig(commonExtension: CommonExtension<*, *, *, *, *, *>) {
    val properties = PropertiesLoader.loadProperties(this)
    commonExtension.apply {
        buildFeatures {
            buildConfig = true
        }

        buildTypes {
            getByName("debug") {
                buildConfigField(
                    "String",
                    "META_APP_ID",
                    "\"${properties.getProperty("metaAppId")}\""
                )
            }
            // Check if release build type is present
            findByName("release")?.apply {
                buildConfigField(
                    "String",
                    "META_APP_ID",
                    "\"${properties.getProperty("metaAppId")}\""
                )
            }
        }
    }
}

internal fun Project.configureAndroid(commonExtension: CommonExtension<*, *, *, *, *, *>) {
    commonExtension.apply {
        namespace = "app.logdate.mobile"

        compileSdk = 34

        defaultConfig {
            minSdk = 29
            testInstrumentationRunner = "app.logdate.mobile.testing.HiltTestRunner"
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
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
 * Configure base Kotlin options for JVM (non-Android)
 */
internal fun Project.configureKotlinJvm() {
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
            jvmTarget.set(JvmTarget.JVM_17)
            val treatWarningsAsErrors: String? by project
            // Treat all Kotlin warnings as errors (disabled by default)
            // Override by setting warningsAsErrors=true in your ~/.gradle/gradle.properties
            allWarningsAsErrors.set(treatWarningsAsErrors.toBoolean())
        }
    }
}
