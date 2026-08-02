package app.logdate.client.media

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Deletes only managed MediaStore rows created by LogDate's Android media importer. */
class AndroidManagedMediaDiscarder(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val contentResolver = context.applicationContext.contentResolver

    suspend fun discard(managedUri: String) {
        val uri = Uri.parse(managedUri)
        require(uri.scheme == "content" && uri.authority == MediaStore.AUTHORITY) {
            "Refusing to discard a URI outside MediaStore"
        }

        withContext(NonCancellable) {
            withContext(ioDispatcher) {
                check(contentResolver.delete(uri, null, null) > 0) {
                    "Managed media no longer exists: $managedUri"
                }
            }
        }
    }
}
