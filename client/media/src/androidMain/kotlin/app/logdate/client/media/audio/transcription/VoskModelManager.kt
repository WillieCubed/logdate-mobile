package app.logdate.client.media.audio.transcription

import android.content.Context
import io.github.aakira.napier.Napier
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Manages the Vosk speech recognition model on disk.
 *
 * On first use, extracts the model from the app's assets into internal storage.
 * Subsequent calls return the cached model path.
 *
 * The model is bundled as a zip file in assets (downloaded at build time by the
 * `app.logdate.vosk-model` Gradle plugin). The zip contains a single top-level
 * directory which is stripped during extraction so model files land directly in
 * the target directory.
 */
class VoskModelManager(
    private val context: Context,
) {
    private val modelDir = File(context.filesDir, MODEL_DIR_NAME)

    /**
     * Returns the path to the extracted Vosk model directory.
     * Extracts from assets on first call; returns cached path on subsequent calls.
     *
     * @return absolute path to the model directory, or null if extraction fails
     */
    suspend fun getModelPath(): String? {
        if (isModelReady()) {
            return modelDir.absolutePath
        }
        return try {
            extractModelFromAssets()
            if (isModelReady()) modelDir.absolutePath else null
        } catch (e: Exception) {
            Napier.e("Failed to extract Vosk model from assets", e)
            null
        }
    }

    private fun isModelReady(): Boolean {
        // A valid Vosk model directory contains at least these files
        val requiredFiles = listOf("am/final.mdl", "conf/mfcc.conf", "graph/Gr.fst")
        return modelDir.exists() && requiredFiles.any { File(modelDir, it).exists() }
    }

    private fun extractModelFromAssets() {
        val assetName = MODEL_ASSET_NAME
        Napier.d("Extracting Vosk model from assets/$assetName")

        // Clean up any partial extraction
        if (modelDir.exists()) {
            modelDir.deleteRecursively()
        }
        modelDir.mkdirs()

        context.assets.open(assetName).use { inputStream ->
            extractZip(inputStream, modelDir)
        }

        Napier.d("Vosk model extracted to ${modelDir.absolutePath}")
    }

    private fun extractZip(
        inputStream: InputStream,
        targetDir: File,
    ) {
        ZipInputStream(inputStream).use { zip ->
            // Vosk model zips contain a single top-level directory (e.g. "vosk-model-small-en-us-0.15/").
            // Strip it so model files land directly in targetDir.
            var topLevelPrefix: String? = null

            var entry = zip.nextEntry
            while (entry != null) {
                val entryName = entry.name

                // Detect the top-level directory from the first entry
                if (topLevelPrefix == null) {
                    val slashIndex = entryName.indexOf('/')
                    if (slashIndex > 0) {
                        topLevelPrefix = entryName.substring(0, slashIndex + 1)
                    }
                }

                // Strip the top-level prefix from entry paths
                val relativePath =
                    if (topLevelPrefix != null && entryName.startsWith(topLevelPrefix)) {
                        entryName.removePrefix(topLevelPrefix)
                    } else {
                        entryName
                    }

                // Skip the top-level directory entry itself
                if (relativePath.isEmpty()) {
                    zip.closeEntry()
                    entry = zip.nextEntry
                    continue
                }

                val file = File(targetDir, relativePath)

                // Prevent zip path traversal
                if (!file.canonicalPath.startsWith(targetDir.canonicalPath)) {
                    throw SecurityException("Zip entry outside target dir: $entryName")
                }

                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { output ->
                        zip.copyTo(output, BUFFER_SIZE)
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    companion object {
        private const val MODEL_DIR_NAME = "vosk-model"
        private const val MODEL_ASSET_NAME = "vosk-model-small-en-us.zip"
        private const val BUFFER_SIZE = 8192
    }
}
