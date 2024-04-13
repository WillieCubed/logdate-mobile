plugins {
    alias(libs.plugins.logdate.android.library)
    alias(libs.plugins.logdate.documentation)
}

android {
    namespace = "app.logdate.core.assist"
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(project(":core:coroutines"))

    // Unit testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
