import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.benManesVersions)
}

subprojects {
    apply {
        // TODO: Migrate to version catalog
        plugin("org.jetbrains.dokka")
        plugin("org.jlleitschuh.gradle.ktlint")
    }

    configure<KtlintExtension> {
        filter {
            exclude { it.file.path.contains("/build/") }
        }
    }

    val dokkaPlugin by configurations
    dependencies {
        // TODO: Figure out how to migrate to version catalog in build configuration
        //noinspection UseTomlInstead
        dokkaPlugin("org.jetbrains.dokka:versioning-plugin:2.2.0-Beta")
    }
}

val atprotoModulePaths =
    listOf(
        ":shared:atproto-crypto",
        ":shared:atproto-syntax",
        ":shared:atproto-identity",
        ":shared:atproto-xrpc",
        ":shared:atproto-repo",
        ":shared:atproto-plc",
        ":shared:atproto-lexicon",
        ":shared:atproto-pds",
        ":shared:atproto-pds-runtime",
    )

tasks.register("generateAtprotoDokka") {
    group = "documentation"
    description = "Generate Dokka HTML publications for every ATProto module."
    dependsOn(atprotoModulePaths.map { "$it:dokkaGeneratePublicationHtml" })
}

tasks.register("publishAtprotoToMavenLocal") {
    group = "publishing"
    description = "Publish every ATProto module to the local Maven repository."
    dependsOn(atprotoModulePaths.map { "$it:publishToMavenLocal" })
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
