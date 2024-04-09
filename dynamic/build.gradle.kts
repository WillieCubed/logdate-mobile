plugins {
    alias(libs.plugins.logdate.dynamic)
}

android {
    namespace = "app.logdate.mobile.instant"
}

dependencies {
    implementation(project(":app:mobile"))
}
