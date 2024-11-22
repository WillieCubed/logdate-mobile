plugins {
    alias(libs.plugins.logdate.android.library)
    alias(libs.plugins.logdate.compose)
    alias(libs.plugins.logdate.documentation)
}

android {
    namespace = "app.logdate.feature.onboarding"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:ui"))
    implementation(project(":core:util"))
    implementation(project(":core:camera"))
    implementation(project(":core:billing"))
    implementation(project(":core:data"))
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.compose.material3.windowsizeclass.android)

    // Compose in logdate.compose build logic
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(project(":feature:editor"))
    implementation(project(":core:notifications")) // TODO: Remove this once editor UI is refactored into separate module
}

// TODO: Extract to build plugin
kotlin {
    sourceSets {
        all {
            languageSettings.optIn("kotlin.uuid.ExperimentalUuidApi")
        }
    }
}