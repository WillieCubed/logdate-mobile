plugins {
    alias(libs.plugins.logdate.android.library)
    alias(libs.plugins.logdate.documentation)
}

android {
    namespace = "app.logdate.core.backup"
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.common)
    implementation(project(":core:coroutines"))
    implementation(project(":core:data"))
    implementation(libs.androidx.work.testing)
    implementation(libs.firebase.messaging.ktx)
    implementation(libs.androidx.hilt.work)
    implementation(project(":core:database"))
    implementation(libs.play.services.auth.blockstore)

    // Unit testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.hilt.android.testing)
}
