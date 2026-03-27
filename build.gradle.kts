@file:Suppress("UnstableApiUsage")

import com.android.build.api.dsl.ApplicationExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.gradle.api.Project

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
        dokkaPlugin("org.jetbrains.dokka:versioning-plugin:2.2.0")
    }

    pluginManager.withPlugin("com.android.application") {
        extensions.configure<ApplicationExtension> {
            configureManagedDevices(project)
        }
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

tasks.register("managedPhoneDebugAndroidTest") {
    group = "verification"
    description = "Run app/android-main instrumented tests on the oldest-supported and flagship managed phones."
    dependsOn(":app:android-main:phoneDevicesGroupDebugAndroidTest")
}

tasks.register("managedTabletDebugAndroidTest") {
    group = "verification"
    description = "Run app/android-main instrumented tests on the managed tablet device."
    dependsOn(":app:android-main:tabletDevicesGroupDebugAndroidTest")
}

tasks.register("managedWearDebugAndroidTest") {
    group = "verification"
    description = "Run app/wear instrumented tests on the shared managed Wear OS device."
    dependsOn(":app:wear:wearDevicesGroupDebugAndroidTest")
}

tasks.register("managedDeviceDebugAndroidTest") {
    group = "verification"
    description = "Run all instrumented tests on the shared managed phone, tablet, and Wear OS device matrix."
    dependsOn(
        ":app:android-main:deviceMatrixGroupDebugAndroidTest",
        "managedWearDebugAndroidTest",
    )
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

private data class ManagedVirtualDeviceConfig(
    val deviceName: String,
    val hardwareProfile: String,
    val apiLevel: Int,
    val systemImageSource: String,
)

private data class ManagedDeviceGroupConfig(
    val groupName: String,
    val targetDeviceNames: List<String>,
)

private data class ManagedDeviceProjectConfig(
    val localDevices: List<ManagedVirtualDeviceConfig>,
    val groups: List<ManagedDeviceGroupConfig>,
)

private fun managedDeviceConfigFor(projectPath: String): ManagedDeviceProjectConfig? =
    when (projectPath) {
        ":app:android-main" ->
            ManagedDeviceProjectConfig(
                localDevices =
                    listOf(
                        ManagedVirtualDeviceConfig(
                            deviceName = "oldestSupportedPhoneApi30",
                            hardwareProfile = "Pixel 2",
                            apiLevel = 30,
                            systemImageSource = "google",
                        ),
                        ManagedVirtualDeviceConfig(
                            deviceName = "flagshipPhoneApi35",
                            hardwareProfile = "Pixel 8 Pro",
                            apiLevel = 35,
                            systemImageSource = "google",
                        ),
                        ManagedVirtualDeviceConfig(
                            deviceName = "largeScreenTabletApi34",
                            hardwareProfile = "Pixel Tablet",
                            apiLevel = 34,
                            systemImageSource = "google",
                        ),
                    ),
                groups =
                    listOf(
                        ManagedDeviceGroupConfig(
                            groupName = "phoneDevices",
                            targetDeviceNames =
                                listOf(
                                    "oldestSupportedPhoneApi30",
                                    "flagshipPhoneApi35",
                                ),
                        ),
                        ManagedDeviceGroupConfig(
                            groupName = "tabletDevices",
                            targetDeviceNames = listOf("largeScreenTabletApi34"),
                        ),
                        ManagedDeviceGroupConfig(
                            groupName = "deviceMatrix",
                            targetDeviceNames =
                                listOf(
                                    "oldestSupportedPhoneApi30",
                                    "flagshipPhoneApi35",
                                    "largeScreenTabletApi34",
                                ),
                        ),
                    ),
            )

        ":app:wear" ->
            ManagedDeviceProjectConfig(
                localDevices =
                    listOf(
                        ManagedVirtualDeviceConfig(
                            deviceName = "wearSmallRoundApi34",
                            hardwareProfile = "Wear OS Small Round",
                            apiLevel = 34,
                            systemImageSource = "google",
                        ),
                    ),
                groups =
                    listOf(
                        ManagedDeviceGroupConfig(
                            groupName = "wearDevices",
                            targetDeviceNames = listOf("wearSmallRoundApi34"),
                        ),
                    ),
            )

        else -> null
    }

private fun ApplicationExtension.configureManagedDevices(project: Project) {
    if (!project.file("src/androidTest").exists()) return

    val config = managedDeviceConfigFor(project.path) ?: return
    testOptions {
        managedDevices {
            val managedDevicesByName =
                config.localDevices.associate { deviceConfig ->
                    val managedDevice =
                        localDevices.findByName(deviceConfig.deviceName)
                            ?: localDevices.maybeCreate(deviceConfig.deviceName).apply {
                                device = deviceConfig.hardwareProfile
                                apiLevel = deviceConfig.apiLevel
                                systemImageSource = deviceConfig.systemImageSource
                            }
                    deviceConfig.deviceName to managedDevice
                }
            groups {
                config.groups.forEach { groupConfig ->
                    if (findByName(groupConfig.groupName) == null) {
                        create(groupConfig.groupName) {
                            groupConfig.targetDeviceNames.forEach { deviceName ->
                                targetDevices.add(checkNotNull(managedDevicesByName[deviceName]))
                            }
                        }
                    }
                }
            }
        }
    }
}
