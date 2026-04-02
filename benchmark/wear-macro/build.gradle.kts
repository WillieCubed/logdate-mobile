import com.android.build.api.dsl.ManagedVirtualDevice
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.test)
}

android {
    namespace = "app.logdate.benchmark.wear"
    compileSdk = 36
    targetProjectPath = ":app:wear"

    defaultConfig {
        minSdk = 31
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        managedDevices {
            lateinit var wearApi34: ManagedVirtualDevice
            localDevices {
                wearApi34 =
                    create("wearApi34") {
                        device = "Wear OS Small Round"
                        apiLevel = 34
                        pageAlignment = ManagedVirtualDevice.PageAlignment.FORCE_16KB_PAGES
                        systemImageSource = "google"
                    }
            }
            groups {
                create("wearBenchmarkDevices") {
                    targetDevices.add(wearApi34)
                }
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
}
