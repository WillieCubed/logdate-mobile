plugins {
    alias(libs.plugins.logdate.android.library)
    alias(libs.plugins.logdate.compose)
}

android {
    namespace = "app.logdate.core.ui"
}

dependencies {
    // Compose in logdate.compose build logic
    implementation(libs.kotlinx.datetime)
    implementation(project(":core:util"))
}
