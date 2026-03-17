plugins {
    id("app.logdate.dynamic-feature")
}

android {
    namespace = "app.logdate.feature.remotedisplay"
}

dependencies {
    implementation(project(":app:android-main"))
    implementation(projects.client.media)
    implementation(libs.napier)
    implementation(libs.kotlinx.coroutines.core)
}
