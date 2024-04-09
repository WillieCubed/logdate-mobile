plugins {
    alias(libs.plugins.logdate.android.library)
    alias(libs.plugins.logdate.compose)
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
    implementation(libs.androidx.material3.window.sizeclass.android)

    // Compose in logdate.compose build logic
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
}
