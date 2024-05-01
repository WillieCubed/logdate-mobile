plugins {
    alias(libs.plugins.logdate.android.library)
    alias(libs.plugins.logdate.documentation)
}

android {
    namespace = "app.logdate.core.updater"
}

dependencies {
    implementation(project(":core:coroutines"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.play.app.update.ktx)

    // Unit testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
