package app.logdate.client.media.storage

import android.system.Os
import android.system.OsConstants
import io.github.aakira.napier.Napier
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.uuid.Uuid

/** Stores attachment bytes in an app-private, content-addressed media store. */
class AndroidCanonicalMediaStore(
    private val filesDir: File,
    private val syncDirectory: (File) -> Unit = ::syncDirectoryOnAndroid,
) {
    private val objectsDirectory = File(filesDir, "media/objects/sha256")
    private val stagingDirectory = File(filesDir, "media/staging")
    private val finalizeLock = Mutex()

    suspend fun store(
        input: InputStream,
        mimeType: String,
        expectedSizeBytes: Long,
    ): String {
        require(expectedSizeBytes >= 0) { "Expected media size cannot be negative" }
        val extension = extensionFor(mimeType)
        ensurePrivateDirectory(stagingDirectory)
        ensurePrivateDirectory(objectsDirectory)
        removeStaleStagingFiles()
        val stagingFile = File(stagingDirectory, "${Uuid.random()}.tmp")

        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var copiedBytes = 0L
            FileOutputStream(stagingFile).use { output ->
                input.use { source ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = source.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        copiedBytes += read
                    }
                }
                output.fd.sync()
            }
            require(copiedBytes == expectedSizeBytes) {
                "Expected $expectedSizeBytes media bytes but copied $copiedBytes"
            }
            currentCoroutineContext().ensureActive()

            val hexDigest = digest.digest().toHex()
            val destinationDirectory = File(objectsDirectory, hexDigest.take(2))
            ensurePrivateDirectory(destinationDirectory)
            val destination = File(destinationDirectory, "$hexDigest.$extension")
            return finalizeLock.withLock {
                currentCoroutineContext().ensureActive()
                if (isVerifiedObject(destination, hexDigest, copiedBytes)) {
                    syncDirectory(destinationDirectory)
                    currentCoroutineContext().ensureActive()
                    return@withLock destination.toURI().toString()
                }
                val quarantinedFile = quarantineCorruptDestination(destination)
                currentCoroutineContext().ensureActive()
                moveIntoPlace(stagingFile, destination)
                syncDirectory(destinationDirectory)
                quarantinedFile?.delete()
                currentCoroutineContext().ensureActive()
                destination.toURI().toString()
            }
        } catch (error: Throwable) {
            withContext(NonCancellable) {
                if (stagingFile.exists() && !stagingFile.delete()) {
                    Napier.w("Unable to remove incomplete private media staging file", error)
                }
            }
            throw error
        }
    }

    private fun isVerifiedObject(
        file: File,
        expectedDigest: String,
        expectedSizeBytes: Long,
    ): Boolean {
        if (!file.isFile || Files.isSymbolicLink(file.toPath()) || file.length() != expectedSizeBytes) {
            return false
        }
        return FileInputStream(file).use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
            digest.digest().toHex() == expectedDigest
        }
    }

    private fun quarantineCorruptDestination(destination: File): File? {
        if (!destination.exists()) return null
        val quarantine = File(stagingDirectory, "${Uuid.random()}.corrupt")
        check(destination.renameTo(quarantine)) { "Unable to quarantine corrupt media object" }
        syncDirectory(checkNotNull(destination.parentFile))
        return quarantine
    }

    private fun removeStaleStagingFiles() {
        val cutoffMillis = System.currentTimeMillis() - STAGING_FILE_MAX_AGE_MILLIS
        stagingDirectory
            .listFiles()
            ?.filter { file ->
                file.isFile &&
                    file.lastModified() <= cutoffMillis &&
                    STAGING_FILE_PATTERN.matches(file.name)
            }?.forEach { file ->
                if (!file.delete()) {
                    Napier.w("Unable to remove stale private media staging file")
                }
            }
    }

    private fun moveIntoPlace(
        stagingFile: File,
        destination: File,
    ) {
        try {
            Files.move(stagingFile.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            check(stagingFile.renameTo(destination)) { "Unable to finalize private media object" }
        }
    }

    private fun ensurePrivateDirectory(directory: File) {
        require(directory.isDirectory || directory.mkdirs()) { "Unable to create private media directory" }
        check(!Files.isSymbolicLink(directory.toPath())) { "Private media directory cannot be a symlink" }
    }

    private fun extensionFor(mimeType: String): String =
        when (mimeType.substringBefore(';').trim().lowercase()) {
            "image/jpeg" -> "jpg"
            "image/heic" -> "heic"
            "image/heic-sequence" -> "heics"
            "image/heif" -> "heif"
            "image/heif-sequence" -> "heifs"
            "image/avif" -> "avif"
            "image/bmp" -> "bmp"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "video/mp4" -> "mp4"
            "video/mpeg" -> "mpg"
            "video/ogg" -> "ogv"
            "video/webm" -> "webm"
            "video/quicktime" -> "mov"
            "video/3gpp" -> "3gp"
            "video/3gpp2" -> "3g2"
            "video/x-matroska" -> "mkv"
            "video/x-msvideo" -> "avi"
            "video/x-ms-wmv" -> "wmv"
            "audio/mp4" -> "m4a"
            "audio/mpeg" -> "mp3"
            "audio/ogg" -> "ogg"
            "audio/aac" -> "aac"
            "audio/amr" -> "amr"
            "audio/flac" -> "flac"
            "audio/midi", "audio/x-midi" -> "mid"
            "audio/opus" -> "opus"
            "audio/3gpp" -> "3gp"
            "audio/3gpp2" -> "3g2"
            "audio/webm" -> "weba"
            "audio/wav", "audio/x-wav" -> "wav"
            else -> throw IllegalArgumentException("Unsupported media MIME type: $mimeType")
        }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        const val STAGING_FILE_MAX_AGE_MILLIS: Long = 24 * 60 * 60 * 1_000L
        val STAGING_FILE_PATTERN = Regex("[0-9a-fA-F-]{36}\\.(tmp|corrupt)")

        fun syncDirectoryOnAndroid(directory: File) {
            if (System.getProperty("java.vm.name") != "Dalvik") return
            val descriptor = Os.open(directory.absolutePath, OsConstants.O_RDONLY, 0)
            try {
                Os.fsync(descriptor)
            } finally {
                Os.close(descriptor)
            }
        }
    }
}
