plugins {
    alias(libs.plugins.logdate.android.library)
    alias(libs.plugins.logdate.compose)
}

android {
    namespace = "app.logdate.core.audiorecorder"
}

dependencies {
    implementation(project(":core:coroutines"))
    implementation(project(":core:media"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.accompanist.permissions)

    // Unit testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
