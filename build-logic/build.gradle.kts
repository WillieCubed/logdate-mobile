plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("de.undercouch:gradle-download-task:5.7.0")
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:2.2.0")
    compileOnly("com.android.tools.build:gradle:9.1.1")
}

gradlePlugin {
    plugins {
        register("atprotoPublishedModule") {
            id = "app.logdate.atproto-published-module"
            implementationClass = "app.logdate.AtprotoPublishedModulePlugin"
        }
        register("speechModel") {
            id = "app.logdate.speech-model"
            implementationClass = "app.logdate.SpeechModelPlugin"
        }
        register("dynamicFeature") {
            id = "app.logdate.dynamic-feature"
            implementationClass = "app.logdate.DynamicFeaturePlugin"
        }
    }
}
