@Suppress("DSL_SCOPE_VIOLATION") // Remove when fixed https://youtrack.jetbrains.com/issue/KTIJ-19369
plugins {
    alias(libs.plugins.logdate.android.library)
}

android {
    namespace = "app.logdate.core.datastore"
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(project(":core:coroutines"))

    // Unit testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
