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
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kover)
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

// Kover configuration for test coverage
kover {
    reports {
        total {
            html {
                onCheck = true
                htmlDir = layout.buildDirectory.dir("reports/kover/html")
            }
            xml {
                onCheck = true
                xmlFile = layout.buildDirectory.file("reports/kover/coverage.xml")
            }
            verify {
                rule {
                    minBound(70) // 70% minimum coverage threshold
                }
            }
        }
        filters {
            excludes {
                // Exclude generated code and build configuration
                classes(
                    "*.BuildConfig*",
                    "*.*Test*",
                    "*.test.*",
                    "*.*_Impl*", // Room generated DAOs
                    "*.*_Factory*", // Koin generated factories
                    "*.di.*Module*", // DI modules
                    "*ComposableSingletons*", // Compose generated code
                    "*.*\$WhenMappings*" // Kotlin when expression mappings
                )
                packages(
                    "*.di", // All DI packages
                    "*.test", // Test packages
                    "*.generated" // Generated code packages
                )
                annotatedBy(
                    "androidx.compose.runtime.Composable", // Exclude Composable functions
                    "org.koin.core.annotation.Module" // Exclude Koin modules
                )
            }
        }
    }
}