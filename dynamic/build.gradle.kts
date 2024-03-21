@Suppress("DSL_SCOPE_VIOLATION") // Remove when fixed https://youtrack.jetbrains.com/issue/KTIJ-19369
plugins {
    alias(libs.plugins.logdate.dynamic)
}

android {
    namespace = "app.logdate.mobile.dynamic"
}

dependencies {
    implementation(project(":app:mobile"))
}
