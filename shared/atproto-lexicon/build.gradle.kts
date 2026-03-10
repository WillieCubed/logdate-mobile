@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("app.logdate.atproto-published-module")
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.kover)
}

val logDateLexiconInputDir = layout.projectDirectory.dir("src/commonMain/resources/studio/hypertext/logdate")
val logDateLexiconOutputDir = layout.projectDirectory.dir("src/commonMain/kotlin/studio/hypertext/atproto/lexicon/generated/logdate")
val atprotoIdentityLexiconInputDir = layout.projectDirectory.dir("src/commonMain/resources/com/atproto/identity")
val atprotoIdentityLexiconOutputDir =
    layout.projectDirectory.dir("src/commonMain/kotlin/studio/hypertext/atproto/lexicon/generated/com/atproto/identity")
val atprotoServerLexiconInputDir = layout.projectDirectory.dir("src/commonMain/resources/com/atproto/server")
val atprotoServerLexiconOutputDir =
    layout.projectDirectory.dir("src/commonMain/kotlin/studio/hypertext/atproto/lexicon/generated/com/atproto/server")
val atprotoRepoLexiconInputDir = layout.projectDirectory.dir("src/commonMain/resources/com/atproto/repo")
val atprotoRepoLexiconOutputDir =
    layout.projectDirectory.dir("src/commonMain/kotlin/studio/hypertext/atproto/lexicon/generated/com/atproto/repo")
val atprotoSyncLexiconInputDir = layout.projectDirectory.dir("src/commonMain/resources/com/atproto/sync")
val atprotoSyncLexiconOutputDir =
    layout.projectDirectory.dir("src/commonMain/kotlin/studio/hypertext/atproto/lexicon/generated/com/atproto/sync")

fun registerLexiconCodegenTask(
    name: String,
    inputDir: org.gradle.api.file.Directory,
    outputDir: org.gradle.api.file.Directory,
    packageName: String,
) {
    tasks.register<JavaExec>(name) {
        group = "codegen"
        description = "Generate checked-in Kotlin models for lexicon documents in ${inputDir.asFile.relativeTo(projectDir)}."

        dependsOn(tasks.named("jvmJar"))

        val jvmJar = tasks.named<Jar>("jvmJar")
        classpath(jvmJar.map { it.archiveFile.get().asFile }, configurations.named("jvmRuntimeClasspath"))
        mainClass.set("studio.hypertext.atproto.lexicon.GenerateLexiconsKt")
        args(
            inputDir.asFile.absolutePath,
            outputDir.asFile.absolutePath,
            packageName,
        )
    }
}

registerLexiconCodegenTask(
    name = "generateLogDateLexicons",
    inputDir = logDateLexiconInputDir,
    outputDir = logDateLexiconOutputDir,
    packageName = "studio.hypertext.atproto.lexicon.generated.logdate",
)

registerLexiconCodegenTask(
    name = "generateAtprotoIdentityLexicons",
    inputDir = atprotoIdentityLexiconInputDir,
    outputDir = atprotoIdentityLexiconOutputDir,
    packageName = "studio.hypertext.atproto.lexicon.generated.com.atproto.identity",
)

registerLexiconCodegenTask(
    name = "generateAtprotoServerLexicons",
    inputDir = atprotoServerLexiconInputDir,
    outputDir = atprotoServerLexiconOutputDir,
    packageName = "studio.hypertext.atproto.lexicon.generated.com.atproto.server",
)

registerLexiconCodegenTask(
    name = "generateAtprotoRepoLexicons",
    inputDir = atprotoRepoLexiconInputDir,
    outputDir = atprotoRepoLexiconOutputDir,
    packageName = "studio.hypertext.atproto.lexicon.generated.com.atproto.repo",
)

registerLexiconCodegenTask(
    name = "generateAtprotoSyncLexicons",
    inputDir = atprotoSyncLexiconInputDir,
    outputDir = atprotoSyncLexiconOutputDir,
    packageName = "studio.hypertext.atproto.lexicon.generated.com.atproto.sync",
)

tasks.register("generateOfficialAtprotoLexicons") {
    group = "codegen"
    description = "Generate checked-in Kotlin models for the official AT Protocol lexicon documents used by this repo."
    dependsOn(
        "generateAtprotoIdentityLexicons",
        "generateAtprotoServerLexicons",
        "generateAtprotoRepoLexicons",
        "generateAtprotoSyncLexicons",
    )
}

description = "Kotlin Multiplatform AT Protocol lexicon parsing and code generation."

kotlin {
    explicitApi()

    android {
        namespace = "studio.hypertext.atproto.lexicon"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    iosArm64()
    iosSimulatorArm64()

    jvm()

    sourceSets {
        commonMain.dependencies {
            api(projects.shared.atprotoSyntax)
            api(libs.kotlinx.serialization.core)
            api(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
