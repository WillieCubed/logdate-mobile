plugins {
    id("app.logdate.dynamic-feature")
}

android {
    namespace = "app.logdate.feature.remotedisplay"
}

dependencies {
    implementation(project(":app:android-main"))
    implementation(projects.client.media)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.common)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.napier)
    implementation(libs.kotlinx.coroutines.core)
}
