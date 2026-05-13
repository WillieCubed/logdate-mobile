package app.logdate.client.media

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.URLConnection
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class AndroidMediaManager(
    private val contentResolver: ContentResolver,
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : MediaManager {
    private val filesDir = context.filesDir
    private val legacyBackfillMutex = Mutex()
    private val recoveryGateway: MediaRecoveryGateway =
        ContentResolverMediaRecoveryGateway(contentResolver, context)

    private enum class MediaKind {
        IMAGE,
        VIDEO,
    }

    override suspend fun getMedia(uri: String): MediaObject =
        withContext(ioDispatcher) {
            // Every helper below either queries ContentResolver or reads metadata
            // through MediaMetadataRetriever for file:// URIs, so the entire
            // body is IO. Keep it dispatched off the caller's thread so this
            // can never freeze a Compose render scope.
            val parsedUri = Uri.parse(uri)
            val fileName = resolveFileName(parsedUri)

            when (resolveMediaKind(parsedUri, fileName)) {
                MediaKind.IMAGE ->
                    if (parsedUri.scheme == ContentResolver.SCHEME_FILE) {
                        getImageMediaFromFileUri(parsedUri)
                    } else {
                        getImageMedia(parsedUri)
                    }
                MediaKind.VIDEO ->
                    if (parsedUri.scheme == ContentResolver.SCHEME_FILE) {
                        getVideoMediaFromFileUri(parsedUri)
                    } else {
                        getVideoMedia(parsedUri)
                    }
            }
        }

    /**
     * Retrieves image media information from the content provider.
     */
    private fun getImageMedia(uri: Uri): MediaObject.Image {
        val cursor =
            contentResolver.query(
                uri,
                arrayOf(
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.SIZE,
                    MediaStore.Images.Media.DATE_TAKEN,
                    MediaStore.Images.Media.DATE_ADDED,
                ),
                null,
                null,
                null,
            )

        return cursor?.use {
            if (!it.moveToFirst()) {
                throw IllegalStateException("Unable to query image metadata for URI: $uri")
            }

            val nameIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dateTakenIndex = it.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
            val dateIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

            it
                .toMediaCursorRow(
                    uri = uri.toString(),
                    nameColumn = nameIndex,
                    sizeColumn = sizeIndex,
                    durationColumn = -1,
                    dateTakenColumn = dateTakenIndex,
                    dateAddedColumn = dateIndex,
                ).toImage(recoveryGateway)
        } ?: throw IllegalStateException("Unable to query image metadata for URI: $uri")
    }

    /**
     * Retrieves video media information from the content provider.
     */
    private fun getVideoMedia(uri: Uri): MediaObject.Video {
        val cursor =
            contentResolver.query(
                uri,
                arrayOf(
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.SIZE,
                    MediaStore.Video.Media.DURATION,
                    MediaStore.Video.Media.DATE_TAKEN,
                    MediaStore.Video.Media.DATE_ADDED,
                ),
                null,
                null,
                null,
            )

        return cursor?.use {
            if (!it.moveToFirst()) {
                throw IllegalStateException("Unable to query video metadata for URI: $uri")
            }

            val nameIndex = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val sizeIndex = it.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val durationIndex = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val dateTakenIndex = it.getColumnIndex(MediaStore.Video.Media.DATE_TAKEN)
            val dateIndex = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

            it
                .toMediaCursorRow(
                    uri = uri.toString(),
                    nameColumn = nameIndex,
                    sizeColumn = sizeIndex,
                    durationColumn = durationIndex,
                    dateTakenColumn = dateTakenIndex,
                    dateAddedColumn = dateIndex,
                ).toVideo(recoveryGateway)
        } ?: throw IllegalStateException("Unable to query video metadata for URI: $uri")
    }

    override suspend fun queryMediaByDate(
        start: Instant,
        end: Instant,
    ): Flow<List<MediaObject>> =
        flow {
            ensureLegacyManagedMediaBackfilled()
            try {
                emit(queryMediaByDateInternal(start, end))
            } catch (error: Exception) {
                Napier.e("Failed to query Android media by date", error)
                throw error
            }
        }

    override suspend fun getRecentMedia(): Flow<List<MediaObject>> =
        flow {
            ensureLegacyManagedMediaBackfilled()
            // Initial snapshot.
            try {
                emit(getRecentMediaInternal())
            } catch (error: Exception) {
                Napier.e("Failed to query recent Android media", error)
                throw error
            }
            // Re-emit a fresh snapshot every time MediaStore reports a change
            // (new capture, deletion, edit). Lets the in-app picker surface a
            // photo taken in the system camera without any manual refresh.
            mediaStoreInvalidations().collect {
                try {
                    emit(getRecentMediaInternal())
                } catch (error: Exception) {
                    Napier.e("Failed to refresh recent Android media after MediaStore change", error)
                }
            }
        }

    /**
     * Emits Unit each time MediaStore's image or video collection changes.
     * The flow registers a [ContentObserver] for the lifetime of each
     * collection and tears it down when the collector cancels.
     */
    private fun mediaStoreInvalidations(): Flow<Unit> =
        callbackFlow {
            val handler = Handler(Looper.getMainLooper())
            val observer =
                object : ContentObserver(handler) {
                    override fun onChange(selfChange: Boolean) {
                        trySend(Unit)
                    }
                }
            val uris =
                listOf(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                )
            uris.forEach { uri ->
                contentResolver.registerContentObserver(uri, true, observer)
            }
            awaitClose { contentResolver.unregisterContentObserver(observer) }
        }.conflate()

    override suspend fun exists(mediaId: String): Boolean {
        val parsedUri = Uri.parse(mediaId)

        return withContext(ioDispatcher) {
            when (parsedUri.scheme) {
                ContentResolver.SCHEME_CONTENT -> queryUriExists(parsedUri)
                ContentResolver.SCHEME_FILE -> requireFileFromUri(parsedUri).exists()
                null, "" -> {
                    queryLegacyMediaStoreIdExists(mediaId) ||
                        File(mediaId).exists() ||
                        File(filesDir, "media/$mediaId").exists() ||
                        File(filesDir, "user_media/$mediaId").exists()
                }
                else -> queryUriExists(parsedUri)
            }
        }
    }

    override suspend fun addToDefaultCollection(uri: String) {
        val parsedUri = Uri.parse(uri)
        val fileName = resolveFileName(parsedUri)
        val mimeType = resolveSupportedMimeType(parsedUri, fileName)

        if (parsedUri.authority == MediaStore.AUTHORITY) {
            return
        }

        if (parsedUri.scheme == ContentResolver.SCHEME_FILE) {
            val sourceFile = requireFileFromUri(parsedUri)
            if (sourceFile.exists() && legacyMediaAlreadyPublished(sourceFile, mimeType)) {
                Napier.d("Media already exists in MediaStore: $uri")
                return
            }
        }

        val publishedUri =
            try {
                publishMediaToStore(
                    sourceUri = parsedUri,
                    fileName = fileName,
                    mimeType = mimeType,
                    timestamp = resolveSourceTimestamp(parsedUri),
                )
            } catch (error: Exception) {
                Napier.e("Failed to publish media to MediaStore", error)
                throw error
            }
        Napier.d("Published media to MediaStore: $publishedUri")
    }

    private suspend fun queryMediaByDateInternal(
        start: Instant,
        end: Instant,
    ): List<MediaObject> =
        withContext(ioDispatcher) {
            val startMillis = start.toEpochMilliseconds()
            val endMillis = end.toEpochMilliseconds()
            val mediaItems = mutableListOf<MediaObject>()

            val imageSelection =
                "(${MediaStore.Images.Media.DATE_TAKEN} >= ? AND ${MediaStore.Images.Media.DATE_TAKEN} < ?) " +
                    "OR (${MediaStore.Images.Media.DATE_TAKEN} IS NULL AND ${MediaStore.Images.Media.DATE_ADDED} >= ? AND ${MediaStore.Images.Media.DATE_ADDED} < ?)"
            val imageCollection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val imageProjection =
                arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.SIZE,
                    MediaStore.Images.Media.DATE_TAKEN,
                    MediaStore.Images.Media.DATE_ADDED,
                )
            val imageSortOrder = "${MediaStore.Images.Media.DATE_TAKEN} ASC, ${MediaStore.Images.Media.DATE_ADDED} ASC"

            requireQueryCursor(
                collectionUri = imageCollection,
                projection = imageProjection,
                selection = imageSelection,
                selectionArgs =
                    arrayOf(
                        startMillis.toString(),
                        endMillis.toString(),
                        (startMillis / 1000).toString(),
                        (endMillis / 1000).toString(),
                    ),
                sortOrder = imageSortOrder,
                failureMessage = "Unable to query Android images for onboarding import",
            ).use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateTakenColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    try {
                        mediaItems.add(
                            imageFromCursor(
                                collectionUri = imageCollection,
                                cursor = cursor,
                                idColumn = idColumn,
                                nameColumn = nameColumn,
                                sizeColumn = sizeColumn,
                                dateTakenColumn = dateTakenColumn,
                                dateAddedColumn = dateColumn,
                            ),
                        )
                    } catch (error: Exception) {
                        Napier.e("Unable to materialize Android image row even with fallbacks", error)
                    }
                }
            }

            val videoSelection =
                "(${MediaStore.Video.Media.DATE_TAKEN} >= ? AND ${MediaStore.Video.Media.DATE_TAKEN} < ?) " +
                    "OR (${MediaStore.Video.Media.DATE_TAKEN} IS NULL AND ${MediaStore.Video.Media.DATE_ADDED} >= ? AND ${MediaStore.Video.Media.DATE_ADDED} < ?)"
            val videoCollection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val videoProjection =
                arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.SIZE,
                    MediaStore.Video.Media.DURATION,
                    MediaStore.Video.Media.DATE_TAKEN,
                    MediaStore.Video.Media.DATE_ADDED,
                )
            val videoSortOrder = "${MediaStore.Video.Media.DATE_TAKEN} ASC, ${MediaStore.Video.Media.DATE_ADDED} ASC"

            requireQueryCursor(
                collectionUri = videoCollection,
                projection = videoProjection,
                selection = videoSelection,
                selectionArgs =
                    arrayOf(
                        startMillis.toString(),
                        endMillis.toString(),
                        (startMillis / 1000).toString(),
                        (endMillis / 1000).toString(),
                    ),
                sortOrder = videoSortOrder,
                failureMessage = "Unable to query Android videos for onboarding import",
            ).use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val dateTakenColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATE_TAKEN)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    try {
                        mediaItems.add(
                            videoFromCursor(
                                collectionUri = videoCollection,
                                cursor = cursor,
                                idColumn = idColumn,
                                nameColumn = nameColumn,
                                sizeColumn = sizeColumn,
                                durationColumn = durationColumn,
                                dateTakenColumn = dateTakenColumn,
                                dateAddedColumn = dateColumn,
                            ),
                        )
                    } catch (error: Exception) {
                        Napier.e("Unable to materialize Android video row even with fallbacks", error)
                    }
                }
            }

            mediaItems.sortBy { it.timestamp }
            mediaItems
        }

    private suspend fun getRecentMediaInternal(): List<MediaObject> =
        withContext(ioDispatcher) {
            val mediaItems = mutableListOf<MediaObject>()

            val imageCollection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val imageProjection =
                arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.SIZE,
                    MediaStore.Images.Media.DATE_TAKEN,
                    MediaStore.Images.Media.DATE_ADDED,
                )
            val imageSortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media.DATE_ADDED} DESC"

            requireQueryCursor(
                collectionUri = imageCollection,
                projection = imageProjection,
                sortOrder = imageSortOrder,
                failureMessage = "Unable to query recent Android images",
            ).use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateTakenColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    try {
                        mediaItems.add(
                            imageFromCursor(
                                collectionUri = imageCollection,
                                cursor = cursor,
                                idColumn = idColumn,
                                nameColumn = nameColumn,
                                sizeColumn = sizeColumn,
                                dateTakenColumn = dateTakenColumn,
                                dateAddedColumn = dateColumn,
                            ),
                        )
                    } catch (error: Exception) {
                        Napier.e("Unable to materialize Android image row even with fallbacks", error)
                    }
                }
            }

            val videoCollection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val videoProjection =
                arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.SIZE,
                    MediaStore.Video.Media.DURATION,
                    MediaStore.Video.Media.DATE_TAKEN,
                    MediaStore.Video.Media.DATE_ADDED,
                )
            val videoSortOrder = "${MediaStore.Video.Media.DATE_TAKEN} DESC, ${MediaStore.Video.Media.DATE_ADDED} DESC"

            requireQueryCursor(
                collectionUri = videoCollection,
                projection = videoProjection,
                sortOrder = videoSortOrder,
                failureMessage = "Unable to query recent Android videos",
            ).use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val dateTakenColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATE_TAKEN)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    try {
                        mediaItems.add(
                            videoFromCursor(
                                collectionUri = videoCollection,
                                cursor = cursor,
                                idColumn = idColumn,
                                nameColumn = nameColumn,
                                sizeColumn = sizeColumn,
                                durationColumn = durationColumn,
                                dateTakenColumn = dateTakenColumn,
                                dateAddedColumn = dateColumn,
                            ),
                        )
                    } catch (error: Exception) {
                        Napier.e("Unable to materialize Android video row even with fallbacks", error)
                    }
                }
            }

            mediaItems
                .sortedByDescending { it.timestamp }
                .take(50)
        }

    override suspend fun readMedia(uri: String): MediaPayload =
        withContext(ioDispatcher) {
            val parsedUri = Uri.parse(uri)
            val fileName = resolveFileName(parsedUri)
            val mimeType = resolveSupportedMimeType(parsedUri, fileName)
            val data = readBytes(parsedUri)
            MediaPayload(
                fileName = fileName,
                mimeType = mimeType,
                sizeBytes = data.size.toLong(),
                data = data,
            )
        }

    override suspend fun saveMedia(payload: MediaPayload): String =
        withContext(ioDispatcher) {
            val mimeType =
                requirePublishableMimeType(
                    payload.mimeType.ifBlank {
                        resolveMimeTypeFromFileName(payload.fileName)
                    },
                    payload.fileName,
                )
            try {
                publishBytesToMediaStore(
                    bytes = payload.data,
                    fileName = payload.fileName,
                    mimeType = mimeType,
                    timestamp = Clock.System.now(),
                )
            } catch (error: Exception) {
                Napier.e("Failed to publish media payload to MediaStore", error)
                throw error
            }
        }

    override suspend fun saveMediaFromFile(
        sourceFilePath: String,
        fileName: String,
        mimeType: String,
    ): String =
        withContext(ioDispatcher) {
            val sourceFile = File(sourceFilePath)
            val publishableMimeType = requirePublishableMimeType(mimeType, fileName)
            try {
                publishFileToMediaStore(
                    sourceFile = sourceFile,
                    fileName = fileName,
                    mimeType = publishableMimeType,
                    timestamp = fileTimestamp(sourceFile),
                )
            } catch (error: Exception) {
                Napier.e("Failed to publish media file to MediaStore", error)
                throw error
            }
        }

    private suspend fun ensureLegacyManagedMediaBackfilled() =
        legacyBackfillMutex.withLock {
            val directory = legacyMediaDirectory()
            if (!directory.exists()) {
                return@withLock
            }

            directory
                .listFiles()
                ?.asSequence()
                ?.filter { it.isFile }
                ?.forEach { file ->
                    val inferredMimeType = resolveMimeTypeFromFileName(file.name) ?: return@forEach
                    if (!isPublishableMimeType(inferredMimeType)) {
                        return@forEach
                    }

                    if (
                        legacyMediaAlreadyPublished(
                            file = file,
                            mimeType = inferredMimeType,
                        )
                    ) {
                        Napier.d("Legacy media file already published in MediaStore: ${file.absolutePath}")
                        return@forEach
                    }

                    runCatching {
                        publishFileToMediaStore(
                            sourceFile = file,
                            fileName = file.name,
                            mimeType = inferredMimeType,
                            timestamp = fileTimestamp(file),
                        )
                    }.onSuccess { publishedUri ->
                        Napier.d("Backfilled legacy media file into MediaStore: $publishedUri")
                    }.onFailure { error ->
                        Napier.e("Failed to backfill legacy media file: ${file.absolutePath}", error)
                    }
                }
        }

    private fun legacyMediaDirectory(): File = File(filesDir, "user_media")

    private fun isPublishableMimeType(mimeType: String): Boolean = mimeType.startsWith("image/") || mimeType.startsWith("video/")

    private fun resolveMimeTypeFromFileName(fileName: String): String? {
        val extension = MimeTypeMap.getFileExtensionFromUrl(fileName)
        val guessedFromExtension = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        return guessedFromExtension ?: URLConnection.guessContentTypeFromName(fileName)
    }

    private fun fileTimestamp(file: File): Instant {
        check(file.exists()) { "Media file does not exist: ${file.absolutePath}" }
        val lastModified = file.lastModified()
        check(lastModified > 0L) { "Unable to resolve lastModified for media file: ${file.absolutePath}" }
        return Instant.fromEpochMilliseconds(lastModified)
    }

    private suspend fun publishMediaToStore(
        sourceUri: Uri,
        fileName: String,
        mimeType: String,
        timestamp: Instant,
    ): String =
        withContext(ioDispatcher) {
            val target =
                mediaStoreTargetForMimeType(mimeType)
                    ?: throw IllegalArgumentException("Unsupported media type: $mimeType")
            val sanitizedName = sanitizeFileName(fileName)
            val values =
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, sanitizedName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, target.relativePath)
                    put(MediaStore.MediaColumns.DATE_TAKEN, timestamp.toEpochMilliseconds())
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

            val insertedUri =
                contentResolver.insert(target.collectionUri, values)
                    ?: throw IllegalStateException("Failed to create MediaStore row for $sourceUri")

            try {
                contentResolver.openOutputStream(insertedUri, "w")?.use { output ->
                    openSourceInputStream(sourceUri).use { input ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException("Failed to open output stream for $insertedUri")

                val cleared =
                    contentResolver.update(
                        insertedUri,
                        ContentValues().apply {
                            put(MediaStore.MediaColumns.IS_PENDING, 0)
                        },
                        null,
                        null,
                    )
                if (cleared <= 0) {
                    throw IllegalStateException("Failed to finalize MediaStore row for $insertedUri")
                }

                insertedUri.toString()
            } catch (e: Exception) {
                contentResolver.delete(insertedUri, null, null)
                throw e
            }
        }

    private suspend fun publishBytesToMediaStore(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        timestamp: Instant,
    ): String =
        publishStreamToMediaStore(
            inputStream = java.io.ByteArrayInputStream(bytes),
            fileName = fileName,
            mimeType = mimeType,
            timestamp = timestamp,
        )

    private suspend fun publishFileToMediaStore(
        sourceFile: File,
        fileName: String,
        mimeType: String,
        timestamp: Instant,
    ): String =
        publishStreamToMediaStore(
            inputStream = FileInputStream(sourceFile),
            fileName = fileName,
            mimeType = mimeType,
            timestamp = timestamp,
        )

    private suspend fun publishStreamToMediaStore(
        inputStream: InputStream,
        fileName: String,
        mimeType: String,
        timestamp: Instant,
    ): String =
        withContext(ioDispatcher) {
            inputStream.use { input ->
                val target =
                    mediaStoreTargetForMimeType(mimeType)
                        ?: throw IllegalArgumentException("Unsupported media type: $mimeType")
                val sanitizedName = sanitizeFileName(fileName)
                val values =
                    ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, sanitizedName)
                        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, target.relativePath)
                        put(MediaStore.MediaColumns.DATE_TAKEN, timestamp.toEpochMilliseconds())
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                val insertedUri =
                    contentResolver.insert(target.collectionUri, values)
                        ?: throw IllegalStateException("Failed to create MediaStore row for $fileName")

                try {
                    contentResolver.openOutputStream(insertedUri, "w")?.use { output ->
                        input.copyTo(output)
                    } ?: throw IllegalStateException("Failed to open output stream for $insertedUri")

                    val cleared =
                        contentResolver.update(
                            insertedUri,
                            ContentValues().apply {
                                put(MediaStore.MediaColumns.IS_PENDING, 0)
                            },
                            null,
                            null,
                        )
                    if (cleared <= 0) {
                        throw IllegalStateException("Failed to finalize MediaStore row for $insertedUri")
                    }

                    insertedUri.toString()
                } catch (e: Exception) {
                    contentResolver.delete(insertedUri, null, null)
                    throw e
                }
            }
        }

    private fun openSourceInputStream(uri: Uri): InputStream =
        when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> FileInputStream(requireFileFromUri(uri))
            else -> contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("Invalid URI: $uri")
        }

    private fun mediaStoreTargetForMimeType(mimeType: String): MediaStoreTarget? =
        when {
            mimeType.startsWith("image/") -> MediaStoreTarget(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "Pictures/LogDate")
            mimeType.startsWith("video/") -> MediaStoreTarget(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "Movies/LogDate")
            else -> null
        }

    private data class MediaStoreTarget(
        val collectionUri: Uri,
        val relativePath: String,
    )

    private fun sanitizeFileName(fileName: String): String =
        fileName
            .replace("..", "_")
            .replace("/", "_")
            .replace("\\", "_")

    private fun requireFileFromUri(uri: Uri): File {
        val path = uri.path ?: throw IllegalArgumentException("File URI is missing a path: $uri")
        return File(path)
    }

    private fun getImageMediaFromFileUri(uri: Uri): MediaObject.Image {
        val file = requireFileFromUri(uri)
        return MediaObject.Image(
            uri = uri.toString(),
            name = file.name,
            size = file.length().toInt(),
            timestamp = fileTimestamp(file),
        )
    }

    private fun getVideoMediaFromFileUri(uri: Uri): MediaObject.Video {
        val file = requireFileFromUri(uri)
        return MediaObject.Video(
            uri = uri.toString(),
            name = file.name,
            size = file.length().toInt(),
            duration = resolveFileVideoDuration(uri),
            timestamp = fileTimestamp(file),
        )
    }

    private fun resolveSourceTimestamp(uri: Uri): Instant =
        when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> fileTimestamp(requireFileFromUri(uri))
            else ->
                querySourceTimestamp(uri)
                    ?: throw IllegalStateException("Unable to resolve timestamp for media URI: $uri")
        }

    private fun querySourceTimestamp(uri: Uri): Instant? {
        val cursor =
            contentResolver.query(
                uri,
                arrayOf(
                    MediaStore.MediaColumns.DATE_TAKEN,
                    MediaStore.MediaColumns.DATE_ADDED,
                ),
                null,
                null,
                null,
            ) ?: return null

        return cursor.use {
            if (!it.moveToFirst()) {
                null
            } else {
                val dateTakenIndex = it.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
                val dateAddedIndex = it.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                resolveTimestampOrNull(dateTakenIndex, dateAddedIndex, it)
            }
        }
    }

    private fun queryUriExists(uri: Uri): Boolean =
        contentResolver
            .query(
                uri,
                arrayOf(MediaStore.MediaColumns._ID),
                null,
                null,
                null,
            )?.use { cursor ->
                cursor.moveToFirst()
            } == true

    private fun queryLegacyMediaStoreIdExists(mediaId: String): Boolean {
        val imageUri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaId)
        val videoUri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, mediaId)
        return queryUriExists(imageUri) || queryUriExists(videoUri)
    }

    private fun legacyMediaAlreadyPublished(
        file: File,
        mimeType: String,
    ): Boolean {
        val target = mediaStoreTargetForMimeType(mimeType) ?: return false
        val sanitizedName = sanitizeFileName(file.name)
        val timestamp = fileTimestamp(file).toEpochMilliseconds()
        return contentResolver
            .query(
                target.collectionUri,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.SIZE} = ? AND ${MediaStore.MediaColumns.DATE_TAKEN} = ?",
                arrayOf(
                    sanitizedName,
                    file.length().toString(),
                    timestamp.toString(),
                ),
                null,
            )?.use { cursor ->
                cursor.moveToFirst()
            } == true
    }

    private fun resolveTimestampOrNull(
        dateTakenColumn: Int,
        dateAddedColumn: Int,
        cursor: Cursor,
    ): Instant? {
        if (dateTakenColumn >= 0) {
            val taken = cursor.getLong(dateTakenColumn)
            if (taken > 0L) {
                return Instant.fromEpochMilliseconds(taken)
            }
        }

        if (dateAddedColumn >= 0) {
            val added = cursor.getLong(dateAddedColumn)
            if (added > 0L) {
                return Instant.fromEpochMilliseconds(added * 1000)
            }
        }

        return null
    }

    private fun resolveFileName(uri: Uri): String {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            return requireFileFromUri(uri).name
        }

        val contentName =
            contentResolver
                .query(
                    uri,
                    arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
                    } else {
                        null
                    }
                }
        return contentName?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Unable to resolve media file name for URI: $uri")
    }

    private fun resolveSupportedMimeType(
        uri: Uri,
        fileName: String,
    ): String {
        val contentType = contentResolver.getType(uri)
        if (!contentType.isNullOrBlank()) {
            return requirePublishableMimeType(contentType, uri.toString())
        }

        return requirePublishableMimeType(resolveMimeTypeFromFileName(fileName), uri.toString())
    }

    private fun readBytes(uri: Uri): ByteArray =
        openSourceInputStream(uri).use { stream ->
            stream.readBytes()
        }

    private fun resolveMediaKind(
        uri: Uri,
        fileName: String,
    ): MediaKind =
        when {
            resolveSupportedMimeType(uri, fileName).startsWith("image/") -> MediaKind.IMAGE
            else -> MediaKind.VIDEO
        }

    private fun requirePublishableMimeType(
        mimeType: String?,
        source: String,
    ): String {
        val resolvedMimeType =
            mimeType?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("Unable to resolve media type for $source")
        if (!isPublishableMimeType(resolvedMimeType)) {
            throw IllegalArgumentException("Unsupported media type for $source: $resolvedMimeType")
        }
        return resolvedMimeType
    }

    /**
     * Resolves a video's duration when the source is a file:// URI. Falls back
     * to [Duration.ZERO] on any failure so the user never loses access to the
     * video just because we can't read its duration metadata.
     */
    private fun resolveFileVideoDuration(uri: Uri): Duration {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(requireFileFromUri(uri).absolutePath)
            val parsed =
                retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?.milliseconds
            if (parsed != null) return parsed

            Napier.w("Missing video duration metadata for file URI: $uri — defaulting to zero so the video stays visible")
            return Duration.ZERO
        } catch (error: RuntimeException) {
            Napier.w("Unable to resolve video duration for file URI: $uri — defaulting to zero so the video stays visible", error)
            return Duration.ZERO
        } finally {
            try {
                retriever.release()
            } catch (error: RuntimeException) {
                Napier.e("Failed to release MediaMetadataRetriever", error)
            }
        }
    }

    private fun requireQueryCursor(
        collectionUri: Uri,
        projection: Array<String>,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: String? = null,
        failureMessage: String,
    ): Cursor =
        contentResolver.query(
            collectionUri,
            projection,
            selection,
            selectionArgs,
            sortOrder,
        ) ?: throw IllegalStateException(failureMessage)

    private fun imageFromCursor(
        collectionUri: Uri,
        cursor: Cursor,
        idColumn: Int,
        nameColumn: Int,
        sizeColumn: Int,
        dateTakenColumn: Int,
        dateAddedColumn: Int,
    ): MediaObject.Image {
        val uri = Uri.withAppendedPath(collectionUri, cursor.getLong(idColumn).toString())
        val row =
            cursor.toMediaCursorRow(
                uri = uri.toString(),
                nameColumn = nameColumn,
                sizeColumn = sizeColumn,
                durationColumn = -1,
                dateTakenColumn = dateTakenColumn,
                dateAddedColumn = dateAddedColumn,
            )
        return row.toImage(recoveryGateway)
    }

    private fun videoFromCursor(
        collectionUri: Uri,
        cursor: Cursor,
        idColumn: Int,
        nameColumn: Int,
        sizeColumn: Int,
        durationColumn: Int,
        dateTakenColumn: Int,
        dateAddedColumn: Int,
    ): MediaObject.Video {
        val uri = Uri.withAppendedPath(collectionUri, cursor.getLong(idColumn).toString())
        val row =
            cursor.toMediaCursorRow(
                uri = uri.toString(),
                nameColumn = nameColumn,
                sizeColumn = sizeColumn,
                durationColumn = durationColumn,
                dateTakenColumn = dateTakenColumn,
                dateAddedColumn = dateAddedColumn,
            )
        return row.toVideo(recoveryGateway)
    }

    /**
     * Extracts a [MediaCursorRow] snapshot from the cursor's current row,
     * keeping every metadata field nullable so the fallback logic in
     * [MediaCursorRow.toImage]/[MediaCursorRow.toVideo] can recover gracefully
     * from missing values. Pass [durationColumn] as -1 for image rows.
     */
    private fun Cursor.toMediaCursorRow(
        uri: String,
        nameColumn: Int,
        sizeColumn: Int,
        durationColumn: Int,
        dateTakenColumn: Int,
        dateAddedColumn: Int,
    ): MediaCursorRow {
        val displayName =
            if (nameColumn >= 0 && !isNull(nameColumn)) getString(nameColumn) else null
        val sizeBytes =
            if (sizeColumn >= 0 && !isNull(sizeColumn)) getInt(sizeColumn) else null
        val durationMillis =
            if (durationColumn >= 0 && !isNull(durationColumn)) getLong(durationColumn) else null
        val dateTakenMillis =
            if (dateTakenColumn >= 0 && !isNull(dateTakenColumn)) getLong(dateTakenColumn) else null
        val dateAddedSeconds =
            if (dateAddedColumn >= 0 && !isNull(dateAddedColumn)) getLong(dateAddedColumn) else null
        return MediaCursorRow(
            uri = uri,
            displayName = displayName,
            sizeBytes = sizeBytes,
            durationMillis = durationMillis,
            dateTakenMillis = dateTakenMillis,
            dateAddedSeconds = dateAddedSeconds,
        )
    }
}
