plugins {
    alias(libs.plugins.logdate.android.library)
    alias(libs.plugins.logdate.documentation)
}

android {
    namespace = "app.logdate.core.testing"
}

dependencies {
    implementation(libs.androidx.test.runner)
    implementation(libs.hilt.android.testing)
}
