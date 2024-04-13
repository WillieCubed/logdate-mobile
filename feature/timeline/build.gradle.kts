plugins {
    alias(libs.plugins.logdate.android.library)
    alias(libs.plugins.logdate.compose)
    alias(libs.plugins.logdate.documentation)
}

android {
    namespace = "app.logdate.feature.timeline"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:model"))
    implementation(project(":core:ui"))
    implementation(project(":core:util"))

    // Compose in logdate.compose build logic
    implementation(libs.androidx.hilt.navigation.compose)
}
