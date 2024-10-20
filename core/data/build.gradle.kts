plugins {
    alias(libs.plugins.logdate.android.library)
    alias(libs.plugins.logdate.documentation)
}

android {
    namespace = "app.logdate.core.data"
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    api(project(":core:datastore"))
    implementation(project(":core:util"))
    implementation(project(":core:coroutines"))
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.installations.ktx)
    implementation(libs.kotlinx.serialization.json)

    // Unit testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
