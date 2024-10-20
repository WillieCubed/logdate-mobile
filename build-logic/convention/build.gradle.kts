plugins {
    `kotlin-dsl`
}

group = "app.logdate.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    compileOnly(libs.android.tools.build.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.hilt.gradle.plugin)
    compileOnly(libs.firebase.perf.plugin)
    compileOnly(libs.dokka.gradle.plugin)
}

gradlePlugin {
    /**
     * Register convention plugins so they are available in the buildlogic scripts of the application
     */
    plugins {
        register("logdateAndroidApplication") {
            id = "logdate.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("logdateAndroidLibrary") {
            id = "logdate.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("logdateAndroidTest") {
            id = "logdate.android.test"
            implementationClass = "AndroidTestConventionPlugin"
        }
        register("logdateJvmLibrary") {
            id = "logdate.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
        register("logdateCompose") {
            id = "logdate.compose"
            implementationClass = "ComposeConventionPlugin"
        }
        register("logdateDynamic") {
            id = "logdate.dynamic"
            implementationClass = "DynamicFeatureConventionPlugin"
        }
        register("logdateDocumentation") {
            id = "logdate.documentation"
            implementationClass = "DokkaConventionPlugin"
        }
        register("logdateSecrets") {
            id = "logdate.secrets"
            implementationClass = "LocalSecretsPlugin"
        }
    }
}
