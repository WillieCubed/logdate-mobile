plugins {
    id("app.logdate.dynamic-feature")
    id("app.logdate.speech-model")
}

android {
    namespace = "app.logdate.feature.speech.recognition"
}

dependencies {
    implementation(project(":app:android-main"))
    implementation(projects.client.media)
    implementation(files("${rootProject.projectDir}/libs/sherpa-onnx-1.12.28.aar"))
    implementation(libs.napier)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.core.ktx)
}
