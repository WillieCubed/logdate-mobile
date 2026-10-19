@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("app.logdate.atproto-published-module")
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.kover)
}

description = "Kotlin Multiplatform AT Protocol repository record primitives."

kotlin {
    explicitApi()

    android {
        namespace = "studio.hypertext.atproto.repo"
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
            api(projects.shared.atprotoIdentity)
            api(projects.shared.atprotoSyntax)
            api(libs.kotlinx.serialization.core)
            api(libs.kotlinx.serialization.json)
            api(libs.okio)
            implementation(libs.napier)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        jvmTest.dependencies {
            implementation(projects.shared.atprotoCrypto)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

// The JVM tests hand exported repositories to the AT Protocol reference implementation
// (@atproto/repo, pinned in tools/atproto-repo-verify) so they are checked by the same code
// relays run. Node.js and pnpm must be on the PATH.
val referenceVerifierDir: Directory = rootProject.layout.projectDirectory.dir("tools/atproto-repo-verify")

val installReferenceRepoVerifier by tasks.registering(Exec::class) {
    description = "Installs the AT Protocol reference implementation that verifies exported repositories."
    group = "verification"
    workingDir(referenceVerifierDir)
    commandLine("pnpm", "install", "--frozen-lockfile", "--silent")
    inputs.file(referenceVerifierDir.file("package.json"))
    inputs.file(referenceVerifierDir.file("pnpm-lock.yaml"))
    outputs.dir(referenceVerifierDir.dir("node_modules"))
}

tasks.named<Test>("jvmTest") {
    dependsOn(installReferenceRepoVerifier)
    inputs.file(referenceVerifierDir.file("verify.mjs"))
    systemProperty("atproto.referenceVerifier", referenceVerifierDir.file("verify.mjs").asFile.absolutePath)
}
