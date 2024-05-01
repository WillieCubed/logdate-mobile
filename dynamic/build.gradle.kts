plugins {
    alias(libs.plugins.logdate.dynamic)
    alias(libs.plugins.logdate.documentation)
}

android {
    namespace = "app.logdate.mobile.instant"
}

dependencies {
    implementation(project(":app:mobile"))
}
