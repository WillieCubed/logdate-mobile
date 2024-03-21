@Suppress("DSL_SCOPE_VIOLATION") // Remove when fixed https://youtrack.jetbrains.com/issue/KTIJ-19369
plugins {
    alias(libs.plugins.logdate.android.library)
    alias(libs.plugins.logdate.compose)
}

android {
    namespace = "app.logdate.feature.editor"
}

dependencies {
    implementation(project(":data"))
    implementation(project(":core:ui"))
    implementation(project(":core:model"))
    implementation(project(":core:util"))

    // Compose in logdate.compose build logic
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(project(":core:world"))
}
