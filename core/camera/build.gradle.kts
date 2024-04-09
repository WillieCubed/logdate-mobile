plugins {
    alias(libs.plugins.logdate.android.library)
    alias(libs.plugins.logdate.compose)
}

android {
    namespace = "app.logdate.core.camera"
}

dependencies {
    implementation(project(":core:coroutines"))
    implementation(project(":core:media"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.camera.core)

    // Unit testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
