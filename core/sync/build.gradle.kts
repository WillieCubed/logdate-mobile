plugins {
    alias(libs.plugins.logdate.android.library)
    alias(libs.plugins.logdate.documentation)
}

android {
    namespace = "app.logdate.core.sync"
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.android)

    // Unit testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
