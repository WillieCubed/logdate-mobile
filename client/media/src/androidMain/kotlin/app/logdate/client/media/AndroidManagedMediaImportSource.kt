package app.logdate.client.media

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Copies an Android content URI into a private staging file before it can be persisted.
 *
 * Android Photo Picker grants are intentionally short-lived. The staging copy is fully
 * flushed and closed before [ManagedMediaImporter] asks [MediaManager] to publish it.
 */
class AndroidManagedMediaImportSource(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ManagedMediaImportSource {
    private val applicationContext = context.applicationContext
    private val contentResolver = applicationContext.contentResolver
    private val stagingDirectory = File(applicationContext.cacheDir, STAGING_DIRECTORY_NAME)

    override suspend fun stage(sourceUri: String): StagedMediaFile {
        var stagedFile: File? = null
        return try {
            withContext(ioDispatcher) {
                val uri = Uri.parse(sourceUri)
                require(uri.scheme == ContentResolver.SCHEME_CONTENT) {
                    "Managed media imports require a content URI"
                }

                val sourceName = queryDisplayName(uri)
                val mediaType = resolveSupportedMediaType(uri, sourceName)
                val fileName = safeImportedFileName(sourceName, mediaType.extension)

                check(stagingDirectory.mkdirs() || stagingDirectory.isDirectory) {
                    "Could not create managed media staging directory"
                }
                stagedFile = File.createTempFile(STAGING_FILE_PREFIX, STAGING_FILE_SUFFIX, stagingDirectory)

                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(checkNotNull(stagedFile)).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val byteCount = input.read(buffer)
                            if (byteCount < 0) break
                            output.write(buffer, 0, byteCount)
                        }
                        output.flush()
                        output.fd.sync()
                    }
                } ?: throw IllegalStateException("Could not read selected media")

                StagedMediaFile(
                    sourceFilePath = checkNotNull(stagedFile).absolutePath,
                    fileName = fileName,
                    mimeType = mediaType.mimeType,
                )
            }
        } catch (error: Exception) {
            withContext(NonCancellable) {
                withContext(ioDispatcher) {
                    stagedFile?.delete()
                }
            }
            throw error
        }
    }

    override suspend fun discard(stagedMedia: StagedMediaFile) {
        withContext(ioDispatcher) {
            val stagedFile = File(stagedMedia.sourceFilePath)
            val safeToDelete =
                runCatching {
                    stagedFile.canonicalFile.parentFile == stagingDirectory.canonicalFile &&
                        stagedFile.name.startsWith(STAGING_FILE_PREFIX)
                }.getOrDefault(false)

            if (!safeToDelete) {
                Napier.w("Refusing to delete a file outside managed media staging")
                return@withContext
            }
            if (stagedFile.exists() && !stagedFile.delete()) {
                Napier.w("Unable to remove staged media import")
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? =
        contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameColumn >= 0 && cursor.moveToFirst()) cursor.getString(nameColumn) else null
            }

    private fun resolveSupportedMediaType(
        uri: Uri,
        sourceName: String?,
    ): SupportedMediaType {
        val extension =
            sourceName
                ?.substringAfterLast('.', missingDelimiterValue = "")
                ?.lowercase()
                ?.takeIf(String::isNotBlank)
        val reportedMimeType =
            contentResolver
                .getType(uri)
                ?.substringBefore(';')
                ?.trim()
                ?.lowercase()
                ?.takeIf(String::isNotBlank)

        return requireNotNull(
            if (reportedMimeType != null) {
                SUPPORTED_MIME_TYPES[reportedMimeType]
            } else {
                extension?.let(SUPPORTED_FILE_EXTENSIONS::get)
            },
        ) {
            "Selected URI is not a supported image or video"
        }
    }
}

private data class SupportedMediaType(
    val mimeType: String,
    val extension: String,
)

private val SUPPORTED_MIME_TYPES =
    mapOf(
        "image/jpeg" to SupportedMediaType("image/jpeg", "jpg"),
        "image/jpg" to SupportedMediaType("image/jpeg", "jpg"),
        "image/png" to SupportedMediaType("image/png", "png"),
        "image/gif" to SupportedMediaType("image/gif", "gif"),
        "image/webp" to SupportedMediaType("image/webp", "webp"),
        "image/heic" to SupportedMediaType("image/heic", "heic"),
        "image/heif" to SupportedMediaType("image/heif", "heif"),
        "image/heic-sequence" to SupportedMediaType("image/heic-sequence", "heics"),
        "image/heif-sequence" to SupportedMediaType("image/heif-sequence", "heifs"),
        "image/avif" to SupportedMediaType("image/avif", "avif"),
        "image/bmp" to SupportedMediaType("image/bmp", "bmp"),
        "image/x-ms-bmp" to SupportedMediaType("image/bmp", "bmp"),
        "video/mp4" to SupportedMediaType("video/mp4", "mp4"),
        "video/webm" to SupportedMediaType("video/webm", "webm"),
        "video/quicktime" to SupportedMediaType("video/quicktime", "mov"),
        "video/3gpp" to SupportedMediaType("video/3gpp", "3gp"),
        "video/3gpp2" to SupportedMediaType("video/3gpp2", "3g2"),
        "video/mpeg" to SupportedMediaType("video/mpeg", "mpg"),
        "video/x-matroska" to SupportedMediaType("video/x-matroska", "mkv"),
        "video/x-msvideo" to SupportedMediaType("video/x-msvideo", "avi"),
        "video/x-ms-wmv" to SupportedMediaType("video/x-ms-wmv", "wmv"),
        "video/ogg" to SupportedMediaType("video/ogg", "ogv"),
    )

private val SUPPORTED_FILE_EXTENSIONS =
    buildMap {
        SUPPORTED_MIME_TYPES.values.forEach { mediaType -> put(mediaType.extension, mediaType) }
        put("jpeg", checkNotNull(SUPPORTED_MIME_TYPES["image/jpeg"]))
        put("jpg", checkNotNull(SUPPORTED_MIME_TYPES["image/jpeg"]))
        put("mpeg", checkNotNull(SUPPORTED_MIME_TYPES["video/mpeg"]))
    }

private const val STAGING_DIRECTORY_NAME = "managed_media_imports"
private const val STAGING_FILE_PREFIX = "logdate_import_"
private const val STAGING_FILE_SUFFIX = ".stage"
