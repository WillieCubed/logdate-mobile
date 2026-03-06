package app.logdate

import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension
import org.gradle.api.DefaultTask
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.net.URI

/**
 * A Vosk model to download and bundle in APK assets.
 */
data class VoskModel(
    val url: String,
    val assetName: String,
)

/**
 * Extension for configuring which Vosk models to bundle.
 */
interface VoskModelExtension {
    val models: ListProperty<VoskModel>
}

/**
 * Downloads Vosk model files into an output directory for AGP asset merging.
 */
abstract class DownloadVoskModelTask : DefaultTask() {
    @get:Input
    abstract val modelUrl: Property<String>

    @get:Input
    abstract val modelFileName: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun download() {
        val outputDir = outputDirectory.get().asFile
        outputDir.mkdirs()
        val dest = outputDir.resolve(modelFileName.get())

        if (dest.exists()) {
            logger.lifecycle("Vosk model already cached: ${dest.name}")
            return
        }

        logger.lifecycle("Downloading Vosk model: ${modelFileName.get()}")
        URI(modelUrl.get()).toURL().openStream().use { input ->
            dest.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        logger.lifecycle("Downloaded Vosk model to ${dest.absolutePath}")
    }
}

/**
 * Convention plugin that downloads Vosk speech recognition models at build time
 * and wires them into Android assets via the AGP variant API.
 *
 * Uses [SourceDirectories.addGeneratedSourceDirectory] so AGP handles task
 * dependency wiring and asset merging automatically — no `afterEvaluate` or
 * task name matching needed.
 *
 * Usage:
 * ```kotlin
 * plugins {
 *     id("app.logdate.vosk-model")
 * }
 *
 * // Optional: override the default English model
 * voskModels {
 *     models.add(VoskModel(
 *         url = "https://alphacephei.com/vosk/models/vosk-model-small-fr-0.22.zip",
 *         assetName = "vosk-model-small-fr.zip",
 *     ))
 * }
 * ```
 */
class VoskModelPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("voskModels", VoskModelExtension::class.java)
        extension.models.convention(listOf(DEFAULT_MODEL))

        val outputDir = project.layout.buildDirectory.dir("generated/vosk-assets")
        val modelUrlProvider = extension.models.map { models -> models.first().url }
        val modelFileNameProvider = extension.models.map { models -> models.first().assetName }

        val downloadTask = project.tasks.register("downloadVoskModels", DownloadVoskModelTask::class.java)
        downloadTask.configure {
            group = "vosk"
            description = "Downloads Vosk speech recognition models for bundling in APK assets"
            outputDirectory.set(outputDir)
            modelUrl.set(modelUrlProvider)
            modelFileName.set(modelFileNameProvider)
        }

        // Wire into AGP's asset pipeline via the KMP Android variant API.
        // addGeneratedSourceDirectory automatically makes asset merge depend on our task.
        project.pluginManager.withPlugin("com.android.kotlin.multiplatform.library") {
            val androidComponents = project.extensions
                .getByType(KotlinMultiplatformAndroidComponentsExtension::class.java)

            androidComponents.onVariants(androidComponents.selector().all()) { variant ->
                variant.sources.assets?.addGeneratedSourceDirectory(
                    downloadTask,
                    DownloadVoskModelTask::outputDirectory,
                )
            }
        }
    }

    companion object {
        private val DEFAULT_MODEL = VoskModel(
            url = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
            assetName = "vosk-model-small-en-us.zip",
        )
    }
}
