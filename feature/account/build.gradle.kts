plugins {
    alias(libs.plugins.logdate.android.library)
    alias(libs.plugins.logdate.compose)
    alias(libs.plugins.logdate.documentation)
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
