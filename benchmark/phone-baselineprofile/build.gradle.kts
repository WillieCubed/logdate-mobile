import com.android.build.api.dsl.ManagedVirtualDevice
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "app.logdate.benchmark.phone.baselineprofile"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()
    targetProjectPath = ":app:android-main"

    defaultConfig {
        minSdk = 30
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }

    experimentalProperties["android.experimental.self-instrumenting"] = true

    buildTypes {
        create("benchmark") {
            isDebuggable = false
            signingConfig = getByName("debug").signingConfig
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs {
            keepDebugSymbols += "**/libbenchmarkNative.so"
            keepDebugSymbols += "**/libtracing_perfetto.so"
        }
    }

    testOptions {
        managedDevices {
            lateinit var flagshipPhoneApi36: ManagedVirtualDevice
            localDevices {
                flagshipPhoneApi36 =
                    create("flagshipPhoneApi36") {
                        device = "Pixel 9 Pro"
                        apiLevel = 36
                        pageAlignment = ManagedVirtualDevice.PageAlignment.FORCE_16KB_PAGES
                        systemImageSource = "google"
                    }
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

baselineProfile {
    managedDevices += "flagshipPhoneApi36"
    useConnectedDevices = false
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator)
}
