plugins {
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.logdate.android.library)
    alias(libs.plugins.logdate.compose)
    alias(libs.plugins.logdate.documentation)
}

android {
    namespace = "app.logdate.feature.timeline"
}

dependencies {
    implementation(projects.core.ui)
    implementation(projects.core.model)
    implementation(projects.core.data)
    implementation(projects.core.media)
    implementation(projects.core.intelligence)
    implementation(projects.core.coroutines)
    implementation(projects.core.util)
    implementation(projects.core.network)

    // Compose in logdate.compose build logic
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.adaptive)
    implementation(libs.androidx.adaptive.layout)
    implementation(libs.androidx.adaptive.navigation)
    implementation(libs.androidx.paging.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor3)
//    implementation(libs.ktor.client.core)
//    implementation(libs.ktor.client.android)
//    implementation(libs.ktor.client.content.negotiation)
//    implementation(libs.ktor.client.logging)
//    implementation(libs.ktor.client.serialization)
//    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.firebase.vertexai)
    implementation(libs.google.maps.compose)
    implementation(libs.multiplatform.markdown.renderer)
}

// TODO: Extract to build plugin
kotlin {
    sourceSets {
        all {
            languageSettings.optIn("kotlin.uuid.ExperimentalUuidApi")
        }
    }
}