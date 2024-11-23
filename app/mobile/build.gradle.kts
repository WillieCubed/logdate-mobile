plugins {
    alias(libs.plugins.logdate.android.application)
    alias(libs.plugins.logdate.compose)
    alias(libs.plugins.logdate.documentation)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.logdate.mobile"

    defaultConfig {
        applicationId = "co.reasonabletech.logdate"
        versionCode = 1
        versionName = "0.1.0"
    }

    dynamicFeatures += setOf(":dynamic")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.windowsizeclass.android)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.biometric)
    implementation(libs.kotlinx.datetime)
    implementation(libs.play.services.instantapps)
    implementation(libs.androidx.adaptive)
    implementation(libs.androidx.adaptive.layout)
    implementation(libs.androidx.adaptive.navigation)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.transition)
//    implementation(libs.androidx.material3.adaptive.navigation.suite.android)

    implementation(project(":feature:onboarding"))
    implementation(project(":feature:account"))
    implementation(project(":feature:timeline"))
    implementation(project(":feature:library"))
    implementation(project(":feature:journals"))
    implementation(project(":feature:rewind"))
    implementation(project(":feature:editor"))
    implementation(project(":feature:widgets"))
    implementation(project(":core:assets"))
    implementation(project(":core:util"))
    implementation(project(":core:ui"))
    implementation(project(":core:assist"))
    implementation(project(":core:data"))
    implementation(project(":core:notifications"))
    implementation(project(":core:theme"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// TODO: Extract to build plugin
kotlin {
    sourceSets {
        all {
            languageSettings.optIn("kotlin.uuid.ExperimentalUuidApi")
        }
    }
}