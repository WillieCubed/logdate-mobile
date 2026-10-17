@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlinx.serialization)
}

description = "LogDate's data models, generated from its lexicon schemas."

// The generator runs on the JVM build of the schema library.
val lexiconCodegen: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm)
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
    }
}

dependencies {
    lexiconCodegen(projects.shared.atprotoLexicon)
}

// The models are generated on every build from lexicons/, never checked in: a schema change
// reaches the code with nothing to run by hand. Generation fails the build if a schema is invalid.
val lexiconSchemas =
    listOf("app/logdate", "community").map { rootProject.layout.projectDirectory.dir("lexicons/$it") }
val generatedModels = layout.buildDirectory.dir("generated/lexicon/kotlin")

val generateLexiconModels =
    tasks.register<JavaExec>("generateLexiconModels") {
        group = "codegen"
        description = "Generate LogDate's Kotlin models from the schemas in lexicons/."
        lexiconSchemas.forEach { inputs.dir(it) }
        outputs.dir(generatedModels)
        classpath(lexiconCodegen)
        mainClass.set("studio.hypertext.atproto.lexicon.GenerateLexiconsKt")
        val outputDirectory = generatedModels.get().asFile
        args(
            lexiconSchemas.joinToString(File.pathSeparator) { it.asFile.absolutePath },
            outputDirectory.absolutePath,
            "app.logdate.lexicon",
            "--namespace-layout",
            "--record-index",
            "LogDateRecords",
        )
        // Start empty, so models for a removed schema don't linger.
        doFirst { outputDirectory.deleteRecursively() }
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
        commonMain {
            kotlin.srcDir(generateLexiconModels.map { generatedModels.get() })
            dependencies {
                api(projects.shared.atprotoLexicon)
            }
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
