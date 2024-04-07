package app.logdate.core.media

import android.content.ContentResolver
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class OnDeviceMediaManager @Inject constructor(
    private val contentResolver: ContentResolver,
) : MediaManager {
    override suspend fun getMedia(uri: Uri): MediaObject {
        val cursor = contentResolver.query(
            uri,
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
                )
            } else {
                throw IllegalArgumentException("Invalid URI: $uri")
            }
        }
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
}