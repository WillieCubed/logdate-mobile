plugins {
    alias(libs.plugins.logdate.android.library)
    alias(libs.plugins.logdate.documentation)
}

android {
    namespace = "app.logdate.core.status"
}

dependencies {
    implementation(project(":core:coroutines"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(project(":core:network"))

    // Unit testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
