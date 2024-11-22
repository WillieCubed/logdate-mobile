plugins {
    alias(libs.plugins.logdate.jvm.library)
    alias(libs.plugins.logdate.documentation)
}

dependencies {
    api(libs.kotlinx.datetime)
}
// TODO: Extract to build plugin
kotlin {
    sourceSets {
        all {
            languageSettings.optIn("kotlin.uuid.ExperimentalUuidApi")
        }
    }
}