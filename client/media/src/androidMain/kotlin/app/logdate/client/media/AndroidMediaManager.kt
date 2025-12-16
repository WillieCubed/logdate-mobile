package app.logdate.client.media

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

class AndroidMediaManager(
    private val contentResolver: ContentResolver,
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : MediaManager {

    private val filesDir = context.filesDir

    override suspend fun getMedia(uri: String): MediaObject {
        val parsedUri = Uri.parse(uri)
        
        // Determine if this is an image or video URI
        val isImage = try {
            contentResolver.getType(parsedUri)?.startsWith("image/") ?: false
        } catch (e: Exception) {
            false
        }
        
        return if (isImage) {
            getImageMedia(parsedUri)
        } else {
            getVideoMedia(parsedUri)
        }
    }
    
    /**
     * Retrieves image media information from the content provider.
     */
    private fun getImageMedia(uri: Uri): MediaObject.Image {
        val cursor = contentResolver.query(
            uri,
            arrayOf(
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_ADDED,
            ),
            null,
            null,
            null
        )
        
        return cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeIndex = it.getColumnIndex(MediaStore.Images.Media.SIZE)
                val dateIndex = it.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
                
                val name = if (nameIndex >= 0) it.getString(nameIndex) else "Unknown"
                val size = if (sizeIndex >= 0) it.getInt(sizeIndex) else 0
                val timestamp = if (dateIndex >= 0) {
                    Instant.fromEpochMilliseconds(it.getLong(dateIndex) * 1000)
                } else {
                    Instant.fromEpochMilliseconds(System.currentTimeMillis())
                }
                
                MediaObject.Image(
                    uri = uri.toString(),
                    name = name,
                    size = size,
                    timestamp = timestamp
                )
            } else {
                // If we couldn't query the content provider, create a basic object with available info
                MediaObject.Image(
                    uri = uri.toString(),
                    name = uri.lastPathSegment ?: "Unknown",
                    size = 0,
                    timestamp = Instant.fromEpochMilliseconds(System.currentTimeMillis())
                )
            }
        } ?: MediaObject.Image(
            uri = uri.toString(),
            name = uri.lastPathSegment ?: "Unknown",
            size = 0,
            timestamp = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        )
    }
    
    /**
     * Retrieves video media information from the content provider.
     */
    private fun getVideoMedia(uri: Uri): MediaObject.Video {
        val cursor = contentResolver.query(
            uri,
            arrayOf(
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.DATE_ADDED,
            ),
            null,
            null,
            null
        )
        
        return cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeIndex = it.getColumnIndex(MediaStore.Video.Media.SIZE)
                val durationIndex = it.getColumnIndex(MediaStore.Video.Media.DURATION)
                val dateIndex = it.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
                
                val name = if (nameIndex >= 0) it.getString(nameIndex) else "Unknown"
                val size = if (sizeIndex >= 0) it.getInt(sizeIndex) else 0
                val duration = if (durationIndex >= 0) it.getLong(durationIndex).milliseconds else Duration.ZERO
                val timestamp = if (dateIndex >= 0) {
                    Instant.fromEpochMilliseconds(it.getLong(dateIndex) * 1000)
                } else {
                    Instant.fromEpochMilliseconds(System.currentTimeMillis())
                }
                
                MediaObject.Video(
                    uri = uri.toString(),
                    name = name,
                    size = size,
                    duration = duration,
                    timestamp = timestamp
                )
            } else {
                MediaObject.Video(
                    uri = uri.toString(),
                    name = uri.lastPathSegment ?: "Unknown",
                    size = 0,
                    duration = Duration.ZERO,
                    timestamp = Instant.fromEpochMilliseconds(System.currentTimeMillis())
                )
            }
        } ?: MediaObject.Video(
            uri = uri.toString(),
            name = uri.lastPathSegment ?: "Unknown",
            size = 0,
            duration = Duration.ZERO,
            timestamp = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        )
    }

    override suspend fun queryMediaByDate(
        start: Instant,
        end: Instant,
    ): Flow<List<MediaObject>> = flow {
        withContext(ioDispatcher) {
            val startMillis = start.toEpochMilliseconds() / 1000 // Convert to seconds for MediaStore
            val endMillis = end.toEpochMilliseconds() / 1000
            val mediaItems = mutableListOf<MediaObject>()
            
            // Query for images
            val imageSelection = "${MediaStore.Images.Media.DATE_ADDED} >= ? AND ${MediaStore.Images.Media.DATE_ADDED} < ?"
            val imageSelectionArgs = arrayOf(startMillis.toString(), endMillis.toString())
            val imageCollection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val imageProjection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_ADDED
            )
            val imageSortOrder = "${MediaStore.Images.Media.DATE_ADDED} ASC"
            
            contentResolver.query(
                imageCollection,
                imageProjection,
                imageSelection,
                imageSelectionArgs,
                imageSortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val size = cursor.getInt(sizeColumn)
                    val dateAdded = cursor.getLong(dateColumn)
                    
                    val contentUri = Uri.withAppendedPath(imageCollection, id.toString())
                    
                    mediaItems.add(
                        MediaObject.Image(
                            uri = contentUri.toString(),
                            name = name,
                            size = size,
                            timestamp = Instant.fromEpochMilliseconds(dateAdded * 1000)
                        )
                    )
                }
            }
            
            // Query for videos
            val videoSelection = "${MediaStore.Video.Media.DATE_ADDED} >= ? AND ${MediaStore.Video.Media.DATE_ADDED} < ?"
            val videoSelectionArgs = arrayOf(startMillis.toString(), endMillis.toString())
            val videoCollection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val videoProjection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.DATE_ADDED
            )
            val videoSortOrder = "${MediaStore.Video.Media.DATE_ADDED} ASC"
            
            contentResolver.query(
                videoCollection,
                videoProjection,
                videoSelection,
                videoSelectionArgs,
                videoSortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val size = cursor.getInt(sizeColumn)
                    val duration = cursor.getLong(durationColumn)
                    val dateAdded = cursor.getLong(dateColumn)
                    
                    val contentUri = Uri.withAppendedPath(videoCollection, id.toString())
                    
                    mediaItems.add(
                        MediaObject.Video(
                            uri = contentUri.toString(),
                            name = name,
                            size = size,
                            duration = duration.milliseconds,
                            timestamp = Instant.fromEpochMilliseconds(dateAdded * 1000)
                        )
                    )
                }
            }
            
            // Sort all media by date
            mediaItems.sortBy { it.timestamp }
            
            emit(mediaItems)
        }
    }

    override suspend fun getRecentMedia(): Flow<List<MediaObject>> = flow {
        withContext(ioDispatcher) {
            // Get recent images and videos
            val mediaItems = mutableListOf<MediaObject>()
            
            // Get images
            val imageCollection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val imageProjection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_ADDED
            )
            val imageSortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC LIMIT 50"
            
            contentResolver.query(
                imageCollection,
                imageProjection,
                null,
                null,
                imageSortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val size = cursor.getInt(sizeColumn)
                    val dateAdded = cursor.getLong(dateColumn)
                    
                    val contentUri = Uri.withAppendedPath(
                        imageCollection,
                        id.toString()
                    )
                    
                    val imageObject = MediaObject.Image(
                        uri = contentUri.toString(),
                        name = name,
                        size = size,
                        timestamp = Instant.fromEpochMilliseconds(dateAdded * 1000)
                    )
                    
                    mediaItems.add(imageObject)
                }
            }
            
            // Get videos
            val videoCollection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val videoProjection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.DATE_ADDED
            )
            val videoSortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC LIMIT 50"
            
            contentResolver.query(
                videoCollection,
                videoProjection,
                null,
                null,
                videoSortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val size = cursor.getInt(sizeColumn)
                    val duration = cursor.getLong(durationColumn)
                    val dateAdded = cursor.getLong(dateColumn)
                    
                    val contentUri = Uri.withAppendedPath(
                        videoCollection,
                        id.toString()
                    )
                    
                    val videoObject = MediaObject.Video(
                        uri = contentUri.toString(),
                        name = name,
                        size = size,
                        duration = duration.milliseconds,
                        timestamp = Instant.fromEpochMilliseconds(dateAdded * 1000)
                    )
                    
                    mediaItems.add(videoObject)
                }
            }
            
            // Sort all media by date, most recent first
            mediaItems.sortByDescending { it.timestamp }
            
            emit(mediaItems)
        }
    }

    override suspend fun exists(mediaId: String): Boolean {
        val uri = Uri.withAppendedPath(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            mediaId
        )
        val cursor = contentResolver.query(
            uri,
            arrayOf(MediaStore.Video.Media._ID),
            null,
            null,
            null
        )
        return cursor.use {
            it != null && it.moveToFirst()
        }
    }

    override suspend fun addToDefaultCollection(uri: String) {
        val mediaId = uri.substringAfterLast('/')
        val destinationPath = getDestinationPath(mediaId)
        copyMediaToAppStorage(Uri.parse(uri), destinationPath)
        Log.d("MediaManager", "Media copied to app storage: $destinationPath")
    }

    private fun getDestinationPath(mediaId: String): String = "$filesDir/user_media/$mediaId"

    private suspend fun copyMediaToAppStorage(uri: Uri, destinationPath: String) =
        withContext(ioDispatcher) {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val outputStream: OutputStream = FileOutputStream(File(destinationPath))

            try {
                if (inputStream != null) {
                    val buffer = ByteArray(1024)
                    var length: Int
                    while (inputStream.read(buffer).also { length = it } > 0) {
                        outputStream.write(buffer, 0, length)
                    }
                } else {
                    throw IllegalArgumentException("Invalid URI: $uri")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                inputStream?.close()
                outputStream.close()
            }
        }
}