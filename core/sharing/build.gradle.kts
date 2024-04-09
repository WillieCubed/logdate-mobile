plugins {
    alias(libs.plugins.logdate.android.library)
}

android {
    namespace = "app.logdate.core.sharing"
}

dependencies {
    implementation(project(":core:media"))
    implementation(project(":core:model"))
    implementation(project(":core:coroutines"))
    implementation(project(":core:data"))
    implementation(project(":core:assets"))

    // Unit testing
    testImplementation(libs.junit)
}
