plugins {
    alias(libs.plugins.logdate.android.library)
    alias(libs.plugins.logdate.documentation)
}

android {
    namespace = "app.logdate.core.billing"
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(project(":core:coroutines"))
    implementation(libs.billing.ktx)

    // Unit testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
