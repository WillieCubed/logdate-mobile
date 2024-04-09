@Suppress("DSL_SCOPE_VIOLATION") // Remove when fixed https://youtrack.jetbrains.com/issue/KTIJ-19369
plugins {
    alias(libs.plugins.logdate.android.library)
    alias(libs.plugins.logdate.compose)
}

android {
    namespace = "app.logdate.feature.account"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:account"))
    implementation(project(":core:util"))

    // Compose in logdate.compose build logic
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.credentials)
    implementation(project(":core:ui"))
}
