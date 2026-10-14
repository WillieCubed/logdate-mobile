package app.logdate.client.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Instant

/** Adds permission-aware, image-only observation without changing general media operations. */
class AndroidPhotoLibraryMediaManager(
    private val delegate: MediaManager,
    private val context: Context,
) : MediaManager by delegate {
    private val resolver = context.contentResolver
    private val recovery: MediaRecoveryGateway = ContentResolverMediaRecoveryGateway(resolver, context)

    override fun photoLibraryAccess(): PhotoLibraryAccess =
        when {
            Build.VERSION.SDK_INT < 33 && granted(Manifest.permission.READ_EXTERNAL_STORAGE) -> PhotoLibraryAccess.FULL
            Build.VERSION.SDK_INT >= 33 && granted(Manifest.permission.READ_MEDIA_IMAGES) -> PhotoLibraryAccess.FULL
            Build.VERSION.SDK_INT >= 34 && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> PhotoLibraryAccess.LIMITED
            else -> PhotoLibraryAccess.DENIED
        }

    private fun granted(permission: String): Boolean = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    override suspend fun observeMediaByDate(
        start: Instant,
        end: Instant,
    ): Flow<List<MediaObject>> = changes().map { queryImages(start, end) }

    private fun changes(): Flow<Unit> =
        callbackFlow {
            val observer =
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        trySend(Unit)
                    }
                }
            resolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
            trySend(Unit)
            awaitClose { resolver.unregisterContentObserver(observer) }
        }.conflate()

    private suspend fun queryImages(
        start: Instant,
        end: Instant,
    ): List<MediaObject> =
        withContext(Dispatchers.IO) {
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val taken = MediaStore.Images.Media.DATE_TAKEN
            val added = MediaStore.Images.Media.DATE_ADDED
            val selection = "($taken >= ? AND $taken < ?) OR (($taken IS NULL OR $taken <= 0) AND $added >= ? AND $added < ?)"
            val args =
                arrayOf(
                    start.toEpochMilliseconds().toString(),
                    end.toEpochMilliseconds().toString(),
                    (start.toEpochMilliseconds() / 1000).toString(),
                    (end.toEpochMilliseconds() / 1000).toString(),
                )
            val projection =
                arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.SIZE,
                    taken,
                    added,
                )
            val cursor =
                resolver.query(collection, projection, selection, args, "$taken ASC, $added ASC")
                    ?: error("Unable to query Android images for marked moments")
            cursor.use {
                buildList {
                    while (it.moveToNext()) add(it.image(collection))
                }
            }
        }

    private fun Cursor.image(collection: Uri): MediaObject.Image {
        val uri = Uri.withAppendedPath(collection, getLong(getColumnIndexOrThrow(MediaStore.Images.Media._ID)).toString())
        return MediaCursorRow(
            uri = uri.toString(),
            displayName = stringOrNull(MediaStore.Images.Media.DISPLAY_NAME),
            sizeBytes = longOrNull(MediaStore.Images.Media.SIZE)?.toInt(),
            durationMillis = null,
            dateTakenMillis = longOrNull(MediaStore.Images.Media.DATE_TAKEN),
            dateAddedSeconds = longOrNull(MediaStore.Images.Media.DATE_ADDED),
        ).toImage(recovery)
    }

    private fun Cursor.longOrNull(column: String): Long? = getColumnIndex(column).let { if (it < 0 || isNull(it)) null else getLong(it) }

    private fun Cursor.stringOrNull(column: String): String? =
        getColumnIndex(column).let {
            if (it < 0 ||
                isNull(it)
            ) {
                null
            } else {
                getString(it)
            }
        }
}
