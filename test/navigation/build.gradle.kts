plugins {
    alias(libs.plugins.logdate.android.test)
    alias(libs.plugins.logdate.documentation)
}

android {
    namespace = "app.logdate.mobile.test.navigation"
    targetProjectPath = ":app:mobile"
}

dependencies {
    implementation(project(":app:mobile"))
    implementation(project(":core:testing"))
    implementation(project(":core:data"))
    implementation(project(":feature:library"))
    implementation(project(":feature:journals"))
}
