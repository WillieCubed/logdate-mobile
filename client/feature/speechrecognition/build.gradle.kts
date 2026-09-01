import com.android.build.api.dsl.ManagedVirtualDevice

plugins {
    id("app.logdate.dynamic-feature")
    id("app.logdate.speech-model")
}

android {
    namespace = "app.logdate.feature.speech.recognition"

    testOptions {
        managedDevices {
            localDevices {
                create("speechRecognitionPhoneApi36") {
                    device = "Pixel 9 Pro"
                    apiLevel = 36
                    systemImageSource = "google"
                    pageAlignment = ManagedVirtualDevice.PageAlignment.FORCE_16KB_PAGES
                }
            }
        }
    }
}

dependencies {
    implementation(project(":app:android-main"))
    implementation(projects.client.media)
    implementation(files("${rootProject.projectDir}/libs/sherpa-onnx-1.12.28.aar"))
    implementation(libs.napier)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.core.ktx)
    // Runtime tar.bz2 extraction for on-demand model downloads (Whisper, CED).
    // Only loaded when this dynamic feature is installed; not in the base APK.
    implementation(libs.commons.compress)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.rules)
}
