@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("app.logdate.lexicon-models")
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlinx.serialization)
}

description = "LogDate's data models, generated from its lexicon schemas."

// The models are generated on every build from lexicons/, never checked in: a schema change
// reaches the code with nothing to run by hand. Generation fails the build if a schema is invalid.
lexiconModels {
    schemas.from(rootProject.file("lexicons/app/logdate"), rootProject.file("lexicons/community"))
    basePackage = "app.logdate.lexicon"
    recordIndex = "LogDateRecords"
}

kotlin {
    explicitApi()

    android {
        namespace = "app.logdate.lexicon"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        withHostTestBuilder {}

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    iosArm64()
    iosSimulatorArm64()

    jvm()

    sourceSets {
        commonMain.dependencies {
            api(projects.shared.atprotoLexicon)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
