package app.logdate.client.media.storage

import android.system.Os
import android.system.OsConstants
import app.logdate.client.device.restore.admitNativeContent
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
import java.net.URI
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
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
        canCommit: () -> Boolean = { true },
    ): String {
        require(expectedSizeBytes >= 0) { "Expected media size cannot be negative" }
        val extension = extensionFor(mimeType)
        admitNativeContent(File(filesDir, "media"))
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
            return finalizeLock.withLock {
                var variant = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    if (!canCommit()) throw CancellationException("Media store commit is no longer valid")
                    val name = if (variant == 0L) "$hexDigest.$extension" else "$hexDigest.retained-$variant.$extension"
                    val destination = File(destinationDirectory, name)
                    if (Files.exists(destination.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                        if (isVerifiedObject(destination, hexDigest, copiedBytes)) {
                            syncDirectory(destinationDirectory)
                            currentCoroutineContext().ensureActive()
                            return@withLock destination.toURI().toString()
                        }
                        // A working entry may still reference this path. Never remove its bytes
                        // merely because an incoming original claims the same digest path.
                        variant++
                        continue
                    }
                    try {
                        moveIntoPlace(stagingFile, destination)
                    } catch (_: FileAlreadyExistsException) {
                        // Another store instance or process claimed this path. Recheck it so
                        // identical imports still share the same reference.
                        continue
                    }
                    syncDirectory(destinationDirectory)
                    currentCoroutineContext().ensureActive()
                    return@withLock destination.toURI().toString()
                }
                error("No available media object path")
            }
        } finally {
            withContext(NonCancellable) {
                val cleanup = runCatching { !stagingFile.exists() || stagingFile.delete() }
                if (!cleanup.getOrDefault(false)) {
                    Napier.w("Unable to remove private media staging file", cleanup.exceptionOrNull())
                }
            }
        }
    }

    /**
     * Deletes a stored object, and reports whether it was there to delete.
     *
     * Refuses anything that does not resolve inside this store's own objects directory. That is
     * the whole safety property: a note's media may be a photo the user picked from their gallery,
     * and deleting the note must not delete their photo. Symlinks are refused for the same reason,
     * since one could point anywhere.
     */
    suspend fun deleteOwned(uri: String): Boolean =
        withContext(Dispatchers.IO) {
            val target =
                runCatching { File(URI(uri)) }
                    .getOrNull()
                    ?.takeIf { it.isFile }
                    ?: return@withContext false

            val objectsRoot =
                runCatching { objectsDirectory.canonicalFile.toPath() }.getOrNull()
                    ?: return@withContext false
            val resolved =
                runCatching { target.canonicalFile.toPath() }.getOrNull()
                    ?: return@withContext false

            if (!resolved.startsWith(objectsRoot)) return@withContext false
            if (Files.isSymbolicLink(target.toPath())) return@withContext false

            target.delete()
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
        // createLink is atomic and refuses an existing destination across processes. A move with
        // ATOMIC_MOVE may replace an existing path on some filesystems even without REPLACE_EXISTING.
        Files.createLink(destination.toPath(), stagingFile.toPath())
    }

    private fun ensurePrivateDirectory(directory: File) {
        require(directory.isDirectory || directory.mkdirs() || directory.isDirectory) { "Unable to create private media directory" }
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

        // Older versions used .corrupt for displaced user bytes. Never expire those files.
        val STAGING_FILE_PATTERN = Regex("[0-9a-fA-F-]{36}\\.tmp")

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
