plugins {
    alias(libs.plugins.logdate.android.library)
}

android {
    namespace = "app.logdate.core.notifications"
}

dependencies {
    implementation(project(":core:coroutines"))
    implementation(project(":core:network"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.datetime)
    implementation(libs.firebase.messaging.ktx)

    // Unit testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
