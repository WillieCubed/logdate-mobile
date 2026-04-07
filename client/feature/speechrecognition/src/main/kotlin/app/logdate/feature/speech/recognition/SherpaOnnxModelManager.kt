package app.logdate.feature.speech.recognition

import android.content.Context
import io.github.aakira.napier.Napier
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Manages the Sherpa-ONNX speech recognition and punctuation models on disk.
 *
 * On first use, extracts models from the app's assets into internal storage.
 * Subsequent calls return the cached model paths.
 *
 * The models are bundled as zip files in assets (downloaded and repackaged at
 * build time by the `app.logdate.speech-model` Gradle plugin). Each zip contains
 * model files with the top-level directory stripped during extraction.
 */
class SherpaOnnxModelManager(
    private val context: Context,
) {
    private val sttModelDir = File(context.filesDir, STT_MODEL_DIR_NAME)
    private val punctModelDir = File(context.filesDir, PUNCT_MODEL_DIR_NAME)
    private val vadModelDir = File(context.filesDir, VAD_MODEL_DIR_NAME)

    suspend fun getModelPath(): String? {
        if (isSttModelReady()) {
            return sttModelDir.absolutePath
        }
        return try {
            extractModelFromAssets(STT_ASSET_NAME, sttModelDir)
            if (isSttModelReady()) sttModelDir.absolutePath else null
        } catch (e: Exception) {
            Napier.e("Failed to extract Sherpa-ONNX STT model from assets", e)
            null
        }
    }

    suspend fun getPunctuationModelPath(): String? {
        if (isPunctModelReady()) {
            return punctModelDir.absolutePath
        }
        return try {
            extractModelFromAssets(PUNCT_ASSET_NAME, punctModelDir)
            if (isPunctModelReady()) punctModelDir.absolutePath else null
        } catch (e: Exception) {
            Napier.e("Failed to extract Sherpa-ONNX punctuation model from assets", e)
            null
        }
    }

    /**
     * Returns the absolute path to the Silero VAD model file, extracting it from
     * assets on first use. Returns null if extraction fails.
     */
    suspend fun getVadModelPath(): String? {
        val modelFile = vadModelDir.resolve(VAD_MODEL_FILE_NAME)
        if (modelFile.exists()) {
            return modelFile.absolutePath
        }
        return try {
            extractModelFromAssets(VAD_ASSET_NAME, vadModelDir)
            if (modelFile.exists()) modelFile.absolutePath else null
        } catch (e: Exception) {
            Napier.e("Failed to extract Silero VAD model from assets", e)
            null
        }
    }

    private fun isSttModelReady(): Boolean =
        sttModelDir.exists() &&
            sttModelDir.resolve("tokens.txt").exists() &&
            sttModelDir.listFiles()?.any { it.extension == "onnx" } == true

    private fun isPunctModelReady(): Boolean =
        punctModelDir.exists() &&
            punctModelDir.listFiles()?.any { it.extension == "onnx" } == true

    private fun extractModelFromAssets(
        assetName: String,
        targetDir: File,
    ) {
        Napier.d("Extracting model from assets/$assetName")

        if (targetDir.exists()) {
            targetDir.deleteRecursively()
        }
        targetDir.mkdirs()

        context.assets.open(assetName).use { inputStream ->
            extractZip(inputStream, targetDir)
        }

        Napier.d("Model extracted to ${targetDir.absolutePath}")
    }

    private fun extractZip(
        inputStream: InputStream,
        targetDir: File,
    ) {
        ZipInputStream(inputStream).use { zip ->
            var topLevelPrefix: String? = null

            var entry = zip.nextEntry
            while (entry != null) {
                val entryName = entry.name

                if (topLevelPrefix == null) {
                    val slashIndex = entryName.indexOf('/')
                    if (slashIndex > 0) {
                        topLevelPrefix = entryName.substring(0, slashIndex + 1)
                    }
                }

                val relativePath =
                    if (topLevelPrefix != null && entryName.startsWith(topLevelPrefix)) {
                        entryName.removePrefix(topLevelPrefix)
                    } else {
                        entryName
                    }

                if (relativePath.isEmpty()) {
                    zip.closeEntry()
                    entry = zip.nextEntry
                    continue
                }

                val file = File(targetDir, relativePath)

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
        private const val STT_MODEL_DIR_NAME = "sherpa-onnx-stt"
        private const val STT_ASSET_NAME = "sherpa-onnx-stt-en.zip"
        private const val PUNCT_MODEL_DIR_NAME = "sherpa-onnx-punct"
        private const val PUNCT_ASSET_NAME = "sherpa-onnx-punct-en.zip"
        private const val VAD_MODEL_DIR_NAME = "sherpa-onnx-vad"
        private const val VAD_ASSET_NAME = "silero-vad.zip"
        private const val VAD_MODEL_FILE_NAME = "silero_vad.onnx"
        private const val BUFFER_SIZE = 8192
    }
}
