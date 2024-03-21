@Suppress("DSL_SCOPE_VIOLATION") // Remove when fixed https://youtrack.jetbrains.com/issue/KTIJ-19369
plugins {
    alias(libs.plugins.logdate.android.application)
    alias(libs.plugins.logdate.compose)
}

android {
    namespace = "app.logdate.mobile"

    defaultConfig {
        applicationId = "app.logdate.mobile"
    }

    dynamicFeatures += setOf(":dynamic")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.window.sizeclass.android)
    implementation(libs.androidx.compose.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.datetime)
    implementation(libs.google.play.services.instantapps)
    implementation(project(":feature:timeline"))
    implementation(project(":feature:library"))
    implementation(project(":feature:journals"))
    implementation(project(":core:util"))
    implementation(project(":core:ui"))
    implementation(project(":feature:rewind"))
    implementation(project(":feature:editor"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}