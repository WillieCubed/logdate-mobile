plugins {
    alias(libs.plugins.logdate.android.library)
    alias(libs.plugins.logdate.documentation)
}

android {
    namespace = "app.logdate.core.data"
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.database)
    api(projects.core.datastore)
    implementation(projects.core.util)
    implementation(projects.core.coroutines)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.installations.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    api(libs.androidx.paging.runtime) // TODO: Remove once repositories are separated from implementation

    // Unit testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.paging.common)
}

// TODO: Extract to build plugin
kotlin {
    sourceSets {
        all {
            languageSettings.optIn("kotlin.uuid.ExperimentalUuidApi")
        }
    }
}
