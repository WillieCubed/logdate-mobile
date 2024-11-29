import org.jetbrains.dokka.gradle.DokkaMultiModuleTask
import org.jetbrains.dokka.gradle.DokkaTaskPartial

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.dokka) apply false
}

subprojects {
    apply {
        // TODO: Migrate to version catalog
        plugin("org.jetbrains.dokka")
    }

    val dokkaPlugin by configurations
    dependencies {
        // TODO: Figure out how to migrate to version catalog in build configuration
        //noinspection UseTomlInstead
        dokkaPlugin("org.jetbrains.dokka:versioning-plugin:1.9.20")
    }
}

tasks.withType<DokkaTaskPartial>().configureEach {
    dokkaSourceSets.configureEach {
        outputDirectory.set(layout.buildDirectory.dir("dokka/${name}"))
        includes.from("README.md")
    }
}

tasks.withType<DokkaMultiModuleTask>().configureEach {
    moduleName.set(project.name)
    outputDirectory.set(layout.buildDirectory.dir("dokka/$name"))
    includes.from("docs")
}