plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("de.undercouch:gradle-download-task:5.6.0")
    implementation("org.apache.commons:commons-compress:1.26.1")
    compileOnly("com.android.tools.build:gradle:9.0.1")
}

gradlePlugin {
    plugins {
        register("speechModel") {
            id = "app.logdate.speech-model"
            implementationClass = "app.logdate.SpeechModelPlugin"
        }
    }
}
