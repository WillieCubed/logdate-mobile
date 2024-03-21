@Suppress("DSL_SCOPE_VIOLATION") // Remove when fixed https://youtrack.jetbrains.com/issue/KTIJ-19369
plugins {
    alias(libs.plugins.logdate.android.test)
}

android {
    namespace = "app.logdate.mobile.test.navigation"
    targetProjectPath = ":app:mobile"
}

dependencies {
    implementation(project(":app:mobile"))
    implementation(project(":core:testing"))
    implementation(project(":data"))
    implementation(project(":feature:library"))
    implementation(project(":feature:journals"))
}
