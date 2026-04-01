@file:Suppress("UnstableApiUsage")

import com.android.build.api.dsl.ApplicationExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.androidx.benchmark) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.benManesVersions)
    jacoco
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

private val featureCoverageModules =
    setOf(
        ":app:android-main",
        ":app:wear",
    )

subprojects {
    if (path in featureCoverageModules) {
        apply(plugin = "jacoco")

        tasks.withType<Test>().configureEach {
            extensions.configure(JacocoTaskExtension::class) {
                isIncludeNoLocationClasses = true
                excludes = listOf("jdk.internal.*")
            }
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

tasks.register<Exec>("managedTabletMediaCoverageAndroidTest") {
    group = "verification"
    description = "Run AndroidMediaManager instrumentation coverage on the managed tablet device."
    workingDir = rootDir
    commandLine(
        rootDir.resolve("gradlew").absolutePath,
        ":app:android-main:createManagedDeviceDebugAndroidTestCoverageReport",
        "-Plogdate.managedDeviceProfile=tabletOnly",
        "-Plogdate.androidTestClass=app.logdate.client.media.AndroidMediaManagerTest",
    )
}

tasks.register("managedWearDebugAndroidTest") {
    group = "verification"
    description = "Run app/wear instrumented tests on the shared managed Wear OS device."
    dependsOn(":app:wear:wearDevicesGroupDebugAndroidTest")
}

tasks.register("managedPhoneBenchmark") {
    group = "verification"
    description = "Run phone macrobenchmarks on the shared managed benchmark device."
    dependsOn(":benchmark:phone-macro:phoneBenchmarkDevicesGroupBenchmarkAndroidTest")
}

tasks.register("managedWearBenchmark") {
    group = "verification"
    description = "Run Wear OS macrobenchmarks on the shared managed benchmark device."
    dependsOn(":benchmark:wear-macro:wearBenchmarkDevicesGroupBenchmarkAndroidTest")
}

tasks.register("managedMicroBenchmark") {
    group = "verification"
    description = "Run Android microbenchmarks on the managed phone device."
    dependsOn(":benchmark:micro:microBenchmarkDevicesGroupReleaseAndroidTest")
}

tasks.register("managedBenchmark") {
    group = "verification"
    description = "Run all managed phone and Wear benchmark suites."
    dependsOn(
        "managedPhoneBenchmark",
        "managedWearBenchmark",
    )
}

tasks.register("generatePhoneBaselineProfile") {
    group = "verification"
    description = "Generate and copy the phone app baseline profile."
    dependsOn(":benchmark:phone-baselineprofile:collectNonMinifiedReleaseBaselineProfile")
}

tasks.register("generateWearBaselineProfile") {
    group = "verification"
    description = "Generate and copy the Wear app baseline profile."
    dependsOn(":benchmark:wear-baselineprofile:collectNonMinifiedReleaseBaselineProfile")
}

tasks.register("managedDeviceDebugAndroidTest") {
    group = "verification"
    description = "Run all instrumented tests on the shared managed phone, tablet, and Wear OS device matrix."
    dependsOn(
        ":app:android-main:deviceMatrixGroupDebugAndroidTest",
        "managedWearDebugAndroidTest",
    )
}

tasks.register<JacocoReport>("featureCoverageReport") {
    group = "verification"
    description = "Generate coverage for the Wear synced-audio playback feature."

    val wearProject = project(":app:wear")
    val phoneProject = project(":app:android-main")
    val composeProject = project(":app:compose-main")
    val mediaProject = project(":client:media")

    dependsOn(
        ":app:wear:testDebugUnitTest",
        ":app:android-main:testDebugUnitTest",
    )

    executionData.setFrom(
        files(
            wearProject.layout.buildDirectory.file("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec"),
            phoneProject.layout.buildDirectory.file("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec"),
        ),
    )

    classDirectories.setFrom(
        files(
            wearProject.fileTree(
                wearProject.layout.buildDirectory
                    .dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes")
                    .get()
                    .asFile,
            ) {
                include(
                    "app/logdate/wear/playback/PhoneSyncedAudioResolver*.class",
                    "app/logdate/wear/presentation/timeline/WearTimelineViewModel*.class",
                    "app/logdate/wear/sync/GoogleWearDataLayerClient*.class",
                )
            },
            composeProject.fileTree(
                composeProject.layout.buildDirectory
                    .dir("classes/kotlin/android/main")
                    .get()
                    .asFile,
            ) {
                include(
                    "app/logdate/client/sync/PhoneDataLayerListenerService*.class",
                    "app/logdate/client/sync/DefaultPhoneWearSyncBridge*.class",
                    "app/logdate/client/sync/GooglePhoneWearTransport*.class",
                    "app/logdate/client/sync/AndroidPhoneAudioStreamOpener*.class",
                )
            },
            mediaProject.fileTree(
                mediaProject.layout.buildDirectory
                    .dir("classes/kotlin/android/main")
                    .get()
                    .asFile,
            ) {
                include("app/logdate/client/media/audio/AndroidAudioPlaybackManager*.class")
            },
        ),
    )

    sourceDirectories.setFrom(
        files(
            wearProject.layout.projectDirectory.dir("src/main/kotlin"),
            composeProject.layout.projectDirectory.dir("src/androidMain/kotlin"),
            mediaProject.layout.projectDirectory.dir("src/androidMain/kotlin"),
        ),
    )
    additionalSourceDirs.setFrom(sourceDirectories)

    reports {
        xml.required.set(true)
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/featureCoverageReport/featureCoverageReport.xml"))
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/featureCoverageReport/html"))
        csv.required.set(false)
    }

    doFirst {
        val missingExecFiles =
            executionData.files.filterNot { it.exists() }
        if (missingExecFiles.isNotEmpty()) {
            throw GradleException(
                "Missing JaCoCo execution data for feature coverage: " +
                    missingExecFiles.joinToString { it.absolutePath },
            )
        }
    }
}

tasks.register<JacocoReport>("mediaAndroidTestCoverageReport") {
    group = "verification"
    description = "Generate Android instrumentation coverage for AndroidMediaManager."

    val appProject = project(":app:android-main")
    val mediaProject = project(":client:media")
    val managedDeviceCoverageFiles =
        files(
            appProject.layout.buildDirectory.file(
                "outputs/managed_device_code_coverage/debug/largeScreenTabletApi34/coverage.ec",
            ),
            appProject.layout.buildDirectory.file(
                "intermediates/managed_device_code_coverage/debugAndroidTest/largeScreenTabletApi34DebugAndroidTest/coverage.ec",
            ),
        )

    dependsOn("managedTabletMediaCoverageAndroidTest")

    executionData.setFrom(
        managedDeviceCoverageFiles,
    )

    classDirectories.setFrom(
        files(
            mediaProject.fileTree(
                mediaProject.layout.buildDirectory
                    .dir("classes/kotlin/android/main")
                    .get()
                    .asFile,
            ) {
                include("app/logdate/client/media/AndroidMediaManager*.class")
            },
        ),
    )

    sourceDirectories.setFrom(
        files(
            mediaProject.layout.projectDirectory.dir("src/androidMain/kotlin"),
        ),
    )
    additionalSourceDirs.setFrom(sourceDirectories)

    reports {
        xml.required.set(true)
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/mediaAndroidTestCoverageReport/mediaAndroidTestCoverageReport.xml"))
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/mediaAndroidTestCoverageReport/html"))
        csv.required.set(false)
    }

    doFirst {
        val coverageFiles =
            executionData.files.filter { it.exists() }
        if (coverageFiles.isEmpty()) {
            throw GradleException(
                "Missing managed-device Android instrumentation coverage data for media coverage under " +
                    managedDeviceCoverageFiles.files.joinToString { it.absolutePath },
            )
        }
    }
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

private val managedDeviceProfile =
    providers.gradleProperty("logdate.managedDeviceProfile").orNull

private fun managedDeviceConfigFor(projectPath: String): ManagedDeviceProjectConfig? =
    when (projectPath) {
        ":app:android-main" ->
            if (managedDeviceProfile == "tabletOnly") {
                ManagedDeviceProjectConfig(
                    localDevices =
                        listOf(
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
                                groupName = "tabletDevices",
                                targetDeviceNames = listOf("largeScreenTabletApi34"),
                            ),
                            ManagedDeviceGroupConfig(
                                groupName = "deviceMatrix",
                                targetDeviceNames = listOf("largeScreenTabletApi34"),
                            ),
                        ),
                )
            } else {
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
                                deviceName = "flagshipPhoneApi36",
                                hardwareProfile = "Pixel 10 Pro",
                                apiLevel = 36,
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
                                        "flagshipPhoneApi36",
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
                                        "flagshipPhoneApi36",
                                        "largeScreenTabletApi34",
                                    ),
                            ),
                        ),
                )
            }

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
