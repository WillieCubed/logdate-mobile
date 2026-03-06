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
    compileOnly("com.android.tools.build:gradle:9.0.1")
}

gradlePlugin {
    plugins {
        register("voskModel") {
            id = "app.logdate.vosk-model"
            implementationClass = "app.logdate.VoskModelPlugin"
        }
    }
}
