plugins {
    alias(libs.plugins.logdate.android.library)
}

android {
    namespace = "app.logdate.core.testing"
}

dependencies {
    implementation(libs.androidx.test.runner)
    implementation(libs.hilt.android.testing)
}
