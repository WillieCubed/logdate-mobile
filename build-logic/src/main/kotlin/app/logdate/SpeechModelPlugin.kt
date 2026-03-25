package app.logdate

import com.android.build.api.variant.DynamicFeatureAndroidComponentsExtension
import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.BufferedInputStream
import java.io.File
import java.net.URI
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class SpeechModel(
    val url: String,
    val assetName: String,
    val excludePatterns: List<String> = emptyList(),
) : java.io.Serializable

interface SpeechModelExtension {
    val models: ListProperty<SpeechModel>
}

abstract class DownloadSpeechModelTask : DefaultTask() {
    @get:Input
    abstract val models: ListProperty<SpeechModel>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun download() {
        val outputDir = outputDirectory.get().asFile
        outputDir.mkdirs()

        for (model in models.get()) {
            val dest = outputDir.resolve(model.assetName)
            if (dest.exists()) {
                logger.lifecycle("Speech model already cached: ${dest.name}")
                continue
            }

            val url = model.url
            if (url.endsWith(".tar.bz2")) {
                downloadAndRepackageTarBz2(url, dest, model.excludePatterns)
            } else {
                downloadDirect(url, dest)
            }
        }
    }

    private fun downloadDirect(url: String, dest: File) {
        logger.lifecycle("Downloading speech model: ${dest.name}")
        URI(url).toURL().openStream().use { input ->
            dest.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        logger.lifecycle("Downloaded speech model to ${dest.absolutePath}")
    }

    private fun downloadAndRepackageTarBz2(url: String, dest: File, excludePatterns: List<String>) {
        logger.lifecycle("Downloading speech model: ${dest.name} (from tar.bz2)")

        val tempTar = File(dest.parentFile, "${dest.name}.tar.bz2.tmp")
        try {
            URI(url).toURL().openStream().use { input ->
                tempTar.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            repackageTarBz2AsZip(tempTar, dest, excludePatterns)
            logger.lifecycle("Repackaged speech model to ${dest.absolutePath}")
        } finally {
            tempTar.delete()
        }
    }

    private fun repackageTarBz2AsZip(tarBz2File: File, zipFile: File, excludePatterns: List<String> = emptyList()) {
        var topLevelPrefix: String? = null

        ZipOutputStream(zipFile.outputStream().buffered()).use { zipOut ->
            TarArchiveInputStream(
                BZip2CompressorInputStream(
                    BufferedInputStream(tarBz2File.inputStream())
                )
            ).use { tarIn ->
                var entry = tarIn.nextEntry
                while (entry != null) {
                    val entryName = entry.name

                    if (topLevelPrefix == null) {
                        val slashIndex = entryName.indexOf('/')
                        if (slashIndex > 0) {
                            topLevelPrefix = entryName.substring(0, slashIndex + 1)
                        }
                    }

                    val relativePath = if (topLevelPrefix != null && entryName.startsWith(topLevelPrefix!!)) {
                        entryName.removePrefix(topLevelPrefix!!)
                    } else {
                        entryName
                    }

                    if (relativePath.isEmpty()) {
                        entry = tarIn.nextEntry
                        continue
                    }

                    if (excludePatterns.any { relativePath.endsWith(it) }) {
                        entry = tarIn.nextEntry
                        continue
                    }

                    if (entry.isDirectory) {
                        zipOut.putNextEntry(ZipEntry("$relativePath/"))
                        zipOut.closeEntry()
                    } else {
                        zipOut.putNextEntry(ZipEntry(relativePath))
                        tarIn.copyTo(zipOut)
                        zipOut.closeEntry()
                    }

                    entry = tarIn.nextEntry
                }
            }
        }
    }
}

class SpeechModelPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("speechModels", SpeechModelExtension::class.java)
        extension.models.convention(DEFAULT_MODELS)

        val outputDir = project.layout.buildDirectory.dir("generated/speech-assets")

        val downloadTask = project.tasks.register("downloadSpeechModels", DownloadSpeechModelTask::class.java)
        downloadTask.configure {
            group = "speech"
            description = "Downloads speech recognition models for bundling in APK assets"
            outputDirectory.set(outputDir)
            models.set(extension.models)
        }

        project.pluginManager.withPlugin("com.android.kotlin.multiplatform.library") {
            val androidComponents = project.extensions
                .getByType(KotlinMultiplatformAndroidComponentsExtension::class.java)

            androidComponents.onVariants(androidComponents.selector().all()) { variant ->
                variant.sources.assets?.addGeneratedSourceDirectory(
                    downloadTask,
                    DownloadSpeechModelTask::outputDirectory,
                )
            }
        }

        project.pluginManager.withPlugin("com.android.dynamic-feature") {
            val androidComponents = project.extensions
                .getByType(DynamicFeatureAndroidComponentsExtension::class.java)

            androidComponents.onVariants(androidComponents.selector().all()) { variant ->
                variant.sources.assets?.addGeneratedSourceDirectory(
                    downloadTask,
                    DownloadSpeechModelTask::outputDirectory,
                )
            }
        }
    }

    companion object {
        private val DEFAULT_MODELS = listOf(
            SpeechModel(
                url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-en-2023-06-26.tar.bz2",
                assetName = "sherpa-onnx-stt-en.zip",
                excludePatterns = listOf(
                    // Keep only int8 quantized models; skip fp32 variants to save ~250MB
                    // Note: decoder has no int8 variant, so keep the fp32 decoder (2MB)
                    "encoder-epoch-99-avg-1-chunk-16-left-128.onnx",
                    "joiner-epoch-99-avg-1-chunk-16-left-128.onnx",
                    ".wav",
                    ".sh",
                    "README.md",
                ),
            ),
            SpeechModel(
                url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/punctuation-models/sherpa-onnx-online-punct-en-2024-08-06.tar.bz2",
                assetName = "sherpa-onnx-punct-en.zip",
                excludePatterns = listOf(
                    "model.int8.onnx",
                    "README.md",
                ),
            ),
        )
    }
}
