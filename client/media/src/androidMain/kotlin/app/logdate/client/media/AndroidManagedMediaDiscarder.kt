package app.logdate.client.media

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Deletes media LogDate's Android media importer just published, whether that landed in the
 * app-private canonical media store (the current importer destination) or, for legacy managed
 * rows, the shared MediaStore.
 */
class AndroidManagedMediaDiscarder(
    context: Context,
    private val mediaManager: MediaManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val packageName = context.applicationContext.packageName
    private val contentResolver = context.applicationContext.contentResolver

    suspend fun discard(managedUri: String) {
        val uri = Uri.parse(managedUri)

        withContext(NonCancellable) {
            withContext(ioDispatcher) {
                when (uri.scheme) {
                    "file" ->
                        check(mediaManager.deleteOwnedMedia(managedUri)) {
                            "Managed media no longer exists: $managedUri"
                        }
                    "content" -> {
                        require(uri.authority == MediaStore.AUTHORITY) {
                            "Refusing to discard a URI outside MediaStore"
                        }
                        require(isOwnedByThisApp(uri)) {
                            "Refusing to discard a MediaStore row this app did not create: $managedUri"
                        }
                        check(contentResolver.delete(uri, null, null) > 0) {
                            "Managed media no longer exists: $managedUri"
                        }
                    }
                    else -> throw IllegalArgumentException("Refusing to discard an unrecognized managed media URI")
                }
            }
        }
    }

    private fun isOwnedByThisApp(uri: Uri): Boolean =
        contentResolver
            .query(uri, arrayOf(MediaStore.MediaColumns.OWNER_PACKAGE_NAME), null, null, null)
            ?.use { cursor ->
                val ownerColumn = cursor.getColumnIndex(MediaStore.MediaColumns.OWNER_PACKAGE_NAME)
                cursor.moveToFirst() && ownerColumn >= 0 && cursor.getString(ownerColumn) == packageName
            } ?: false
}
