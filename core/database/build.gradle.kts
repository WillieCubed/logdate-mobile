plugins {
    alias(libs.plugins.logdate.android.library)
    alias(libs.plugins.logdate.documentation)
}

android {
    namespace = "app.logdate.core.database"
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Unit testing
    testImplementation(libs.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
}
