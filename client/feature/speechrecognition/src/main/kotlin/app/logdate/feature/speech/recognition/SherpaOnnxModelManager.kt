package app.logdate.feature.speech.recognition

import android.content.Context
import app.logdate.client.media.audio.download.ModelDownloadStatus
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
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
    private val whisperModelDir = File(context.filesDir, WHISPER_MODEL_DIR_NAME)
    private val taggingModelDir = File(context.filesDir, TAGGING_MODEL_DIR_NAME)

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

    /**
     * Returns the absolute path to the Whisper small.en model directory if a
     * complete model is present in internal storage, or null otherwise.
     *
     * The model is not bundled with the app — it is downloaded on demand and
     * placed at this path by [downloadWhisperModel]. Until that happens,
     * callers should treat refinement as unavailable and fall back to
     * streaming-only transcription.
     */
    fun getWhisperModelPath(): String? = if (isWhisperModelReady()) whisperModelDir.absolutePath else null

    fun isWhisperModelReady(): Boolean =
        whisperModelDir.exists() &&
            whisperModelDir.resolve(WHISPER_TOKENS_NAME).exists() &&
            whisperModelDir.resolve(WHISPER_ENCODER_NAME).exists() &&
            whisperModelDir.resolve(WHISPER_DECODER_NAME).exists()

    /**
     * Returns the absolute path to the CED-small audio tagging model
     * directory if a complete model is present in internal storage, or null
     * otherwise.
     *
     * The model is not bundled with the app — it is downloaded on demand and
     * placed at this path. Until that happens, callers should treat ambient
     * sound detection as unavailable.
     */
    fun getAudioTaggingModelPath(): String? = if (isAudioTaggingModelReady()) taggingModelDir.absolutePath else null

    fun isAudioTaggingModelReady(): Boolean =
        taggingModelDir.exists() &&
            taggingModelDir.resolve(TAGGING_MODEL_FILE_NAME).exists() &&
            taggingModelDir.resolve(TAGGING_LABELS_FILE_NAME).exists()

    /**
     * Streams the Whisper small.en archive from the Sherpa-ONNX release page,
     * unpacks it into [whisperModelDir], and emits [ModelDownloadStatus]
     * updates as the bytes flow. Cancellable mid-stream.
     */
    fun downloadWhisperModel(): Flow<ModelDownloadStatus> =
        downloadAndExtractTarBz2(
            url = WHISPER_DOWNLOAD_URL,
            targetDir = whisperModelDir,
            isReady = ::isWhisperModelReady,
        )

    /**
     * Streams the CED-small audio tagging archive from the Sherpa-ONNX
     * release page, unpacks it into [taggingModelDir], and emits
     * [ModelDownloadStatus] updates as the bytes flow. Cancellable mid-stream.
     */
    fun downloadAudioTaggingModel(): Flow<ModelDownloadStatus> =
        downloadAndExtractTarBz2(
            url = TAGGING_DOWNLOAD_URL,
            targetDir = taggingModelDir,
            isReady = ::isAudioTaggingModelReady,
        )

    /**
     * Downloads a tar.bz2 archive at [url], streams it through bzip2 + tar
     * decoders, and writes the (top-level-stripped) entries into [targetDir].
     * Emits a [ModelDownloadStatus.Downloading] update on a small interval as
     * bytes accumulate, then a single [ModelDownloadStatus.Extracting] before
     * the disk write phase, then [ModelDownloadStatus.Completed].
     *
     * On any failure the partial [targetDir] is wiped so a retry starts clean.
     * The triggering exception is logged via Napier and never reaches the
     * caller as a string — the caller only sees a [ModelDownloadStatus.Reason].
     */
    private fun downloadAndExtractTarBz2(
        url: String,
        targetDir: File,
        isReady: () -> Boolean,
    ): Flow<ModelDownloadStatus> =
        flow {
            if (isReady()) {
                emit(ModelDownloadStatus.Completed)
                return@flow
            }

            var connection: HttpURLConnection? = null
            try {
                connection =
                    (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                        connectTimeout = HTTP_CONNECT_TIMEOUT_MS
                        readTimeout = HTTP_READ_TIMEOUT_MS
                        instanceFollowRedirects = true
                        requestMethod = "GET"
                    }

                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    Napier.w("Model download HTTP $responseCode for $url")
                    emit(ModelDownloadStatus.ServerUnavailable)
                    return@flow
                }

                val totalBytes =
                    connection.contentLengthLong.takeIf { it > 0 }
                        ?: connection.getHeaderField("Content-Length")?.toLongOrNull()?.takeIf { it > 0 }

                emit(ModelDownloadStatus.Downloading(bytesDownloaded = 0L, totalBytes = totalBytes))

                // Buffer the bytes to a temp file first so the bzip2 + tar
                // decode passes can fail cleanly without leaving the network
                // half-read. Stream-based progress reporting happens here.
                val tempArchive = File(targetDir.parentFile, "${targetDir.name}.tar.bz2.tmp")
                tempArchive.parentFile?.mkdirs()
                tempArchive.delete()

                connection.inputStream.use { input ->
                    FileOutputStream(tempArchive).use { output ->
                        val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                        var totalRead = 0L
                        var bytesSinceLastEmit = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            totalRead += read
                            bytesSinceLastEmit += read
                            if (bytesSinceLastEmit >= PROGRESS_EMIT_INTERVAL_BYTES) {
                                emit(
                                    ModelDownloadStatus.Downloading(
                                        bytesDownloaded = totalRead,
                                        totalBytes = totalBytes,
                                    ),
                                )
                                bytesSinceLastEmit = 0L
                            }
                        }
                        emit(
                            ModelDownloadStatus.Downloading(
                                bytesDownloaded = totalRead,
                                totalBytes = totalBytes,
                            ),
                        )
                    }
                }

                emit(ModelDownloadStatus.Extracting)
                try {
                    extractTarBz2IntoDir(tempArchive, targetDir)
                } finally {
                    tempArchive.delete()
                }

                if (!isReady()) {
                    Napier.w("Extracted archive at $url is missing expected files")
                    targetDir.deleteRecursively()
                    emit(ModelDownloadStatus.ArchiveCorrupt)
                    return@flow
                }

                emit(ModelDownloadStatus.Completed)
            } catch (e: java.net.UnknownHostException) {
                Napier.e("Model download failed: no network for $url", e)
                targetDir.deleteRecursively()
                emit(ModelDownloadStatus.NoNetwork)
            } catch (e: java.net.SocketTimeoutException) {
                Napier.e("Model download timed out for $url", e)
                targetDir.deleteRecursively()
                emit(ModelDownloadStatus.NoNetwork)
            } catch (e: java.io.IOException) {
                // Includes "no space left on device". The JDK doesn't expose a
                // typed signal for this, so we sniff the message internally
                // (logged separately) and bucket anything else as Unknown.
                val outOfSpace = e.message?.contains("space", ignoreCase = true) == true
                Napier.e("Model download IO error for $url (outOfSpace=$outOfSpace)", e)
                targetDir.deleteRecursively()
                emit(if (outOfSpace) ModelDownloadStatus.OutOfStorage else ModelDownloadStatus.UnknownError)
            } catch (e: Exception) {
                Napier.e("Model download failed for $url", e)
                targetDir.deleteRecursively()
                emit(ModelDownloadStatus.UnknownError)
            } finally {
                connection?.disconnect()
            }
        }.flowOn(Dispatchers.IO)

    /**
     * Unpacks a downloaded tar.bz2 archive into [targetDir], stripping the
     * archive's top-level directory the same way the build-time plugin does
     * for bundled assets.
     */
    private fun extractTarBz2IntoDir(
        archive: File,
        targetDir: File,
    ) {
        if (targetDir.exists()) {
            targetDir.deleteRecursively()
        }
        targetDir.mkdirs()

        var topLevelPrefix: String? = null
        TarArchiveInputStream(
            BZip2CompressorInputStream(BufferedInputStream(archive.inputStream())),
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

                val relativePath =
                    topLevelPrefix?.let {
                        if (entryName.startsWith(it)) entryName.removePrefix(it) else entryName
                    } ?: entryName

                if (relativePath.isEmpty()) {
                    entry = tarIn.nextEntry
                    continue
                }

                val outFile = File(targetDir, relativePath)
                if (!outFile.canonicalPath.startsWith(targetDir.canonicalPath)) {
                    throw SecurityException("Tar entry escaped target dir: $entryName")
                }

                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { tarIn.copyTo(it, BUFFER_SIZE) }
                }
                entry = tarIn.nextEntry
            }
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

        const val WHISPER_MODEL_DIR_NAME = "sherpa-onnx-whisper-small-en"
        const val WHISPER_ENCODER_NAME = "small.en-encoder.int8.onnx"
        const val WHISPER_DECODER_NAME = "small.en-decoder.int8.onnx"
        const val WHISPER_TOKENS_NAME = "small.en-tokens.txt"

        const val TAGGING_MODEL_DIR_NAME = "sherpa-onnx-ced-small"
        const val TAGGING_MODEL_FILE_NAME = "model.onnx"
        const val TAGGING_LABELS_FILE_NAME = "class_labels_indices.csv"

        private const val BUFFER_SIZE = 8192
    }
}
