plugins {
    alias(libs.plugins.logdate.android.library)
    alias(libs.plugins.logdate.compose)
    alias(libs.plugins.logdate.documentation)
}

android {
    namespace = "app.logdate.core.ui"
}

dependencies {
    api(projects.core.theme)
    implementation(projects.core.util)
    implementation(projects.core.model)
    // Compose in logdate.compose build logic
    implementation(libs.kotlinx.datetime)
    implementation(libs.coil.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.google.maps.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.adaptive)
    implementation(libs.androidx.adaptive.layout)
    implementation(libs.androidx.adaptive.navigation)
    implementation(libs.multiplatform.markdown.renderer)
}

// TODO: Extract to build plugin
kotlin {
    sourceSets {
        all {
            languageSettings.optIn("kotlin.uuid.ExperimentalUuidApi")
        }
    }
}