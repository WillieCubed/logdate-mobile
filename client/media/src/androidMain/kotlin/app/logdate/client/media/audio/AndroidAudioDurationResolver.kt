package app.logdate.client.media.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import app.logdate.client.media.storage.MediaFileResolver
import app.logdate.client.media.storage.androidMediaFileResolver
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidAudioDurationResolver(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mediaFiles: MediaFileResolver = androidMediaFileResolver(context),
) : AudioDurationResolver {
    override suspend fun resolveDurationMs(uri: String): Long? =
        withContext(ioDispatcher) {
            val retriever = MediaMetadataRetriever()
            try {
                val path = mediaFiles.filePath(uri)
                if (path != null) {
                    retriever.setDataSource(path)
                } else {
                    retriever.setDataSource(context, Uri.parse(uri))
                }

                retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
            } catch (e: Exception) {
                Napier.e("Failed to resolve audio duration for $uri", e)
                null
            } finally {
                try {
                    retriever.release()
                } catch (e: Exception) {
                    Napier.e("Failed to release MediaMetadataRetriever", e)
                }
            }
        }
}
