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
        val cursor = contentResolver.query(
            Uri.parse(uri),
            arrayOf(
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DURATION,
            ),
            null,
            null,
            null
        )
        return cursor.use {
            if (it != null && it.moveToFirst()) {
                MediaObject.Video(
                    uri = uri,
                    name = it.getString(it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)),
                    size = it.getInt(it.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)),
                    duration = it.getLong(it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)),
                    timestamp = Instant.fromEpochMilliseconds(
                        it.getLong(
                            it.getColumnIndexOrThrow(
                                MediaStore.Video.Media.DATE_ADDED
                            )
                        )
                    ),
                )
            } else {
                throw IllegalArgumentException("Invalid URI: $uri")
            }
        }
    }

    override suspend fun queryMediaByDate(
        start: Instant,
        end: Instant,
    ): Flow<List<MediaObject>> {
        val startMillis = start.toEpochMilliseconds()
        val endMillis = end.toEpochMilliseconds()
        val collection = MediaStore.Video.Media.getContentUri(
            MediaStore.VOLUME_EXTERNAL
        )
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE
        )
        val selection =
            "${MediaStore.Video.Media.DATE_ADDED} >= ? AND ${MediaStore.Video.Media.DATE_ADDED} < ?"
        val selectionArgs = arrayOf(
            startMillis.toString(),
            endMillis.toString()
        )
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} ASC"

        val cursor = contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )
        @Suppress("UNCHECKED_CAST")
        return (cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val durationColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            generateSequence {
                if (it.moveToNext()) {
                    MediaObject.Video(
                        uri = Uri.withAppendedPath(
                            collection,
                            it.getString(idColumn)
                        ).toString(),
                        name = it.getString(nameColumn),
                        size = it.getInt(sizeColumn),
                        duration = it.getLong(durationColumn),
                        timestamp = Instant.fromEpochMilliseconds(
                            it.getLong(
                                it.getColumnIndexOrThrow(
                                    MediaStore.Video.Media.DATE_ADDED
                                )
                            )
                        ),
                    )
                } else {
                    null
                }
            }
        }?.asFlow() ?: emptyFlow()) as Flow<List<MediaObject>>
    }

    override suspend fun getRecentMedia(): Flow<List<MediaObject>> {
        // TODO: Fetch all image and video media objects from the device
        return flow {
            emit(listOf())
        }

//        val collection = MediaStore.Video.Media.getContentUri(
//            MediaStore.VOLUME_EXTERNAL
//        )
//        val projection = arrayOf(
//            MediaStore.Video.Media._ID,
//            MediaStore.Video.Media.DISPLAY_NAME,
//            MediaStore.Video.Media.DURATION,
//            MediaStore.Video.Media.SIZE
//        )
//        // Show only videos that are at least 5 minutes in duration.
//        val selection = "${MediaStore.Video.Media.DURATION} >= ?"
//        val selectionArgs = arrayOf(
//            TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES).toString()
//        )
//
//        // Display videos in alphabetical order based on their display name.
//        val sortOrder = "${MediaStore.Video.Media.DISPLAY_NAME} ASC"
//
//        val cursor = contentResolver.query(
//            collection,
//            projection,
//            selection,
//            selectionArgs,
//            sortOrder
//        )
//        return cursor?.use {
//            val idColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
//            val nameColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
//            val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
//            generateSequence {
//                if (it.moveToNext()) {
//                    MediaObject(
//                        uri = Uri.withAppendedPath(
//                            collection,
//                            it.getString(idColumn)
//                        ),
//                        name = it.getString(nameColumn),
//                        size = it.getLong(sizeColumn)
//                    )
//                } else {
//                    null
//                }
//            }
//        }?.asFlow() ?: emptyFlow()
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