package app.logdate.client.media.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidAudioDurationResolver(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AudioDurationResolver {
    override suspend fun resolveDurationMs(uri: String): Long? = withContext(ioDispatcher) {
        val retriever = MediaMetadataRetriever()
        try {
            val parsedUri = Uri.parse(uri)
            if (parsedUri.scheme.isNullOrBlank()) {
                retriever.setDataSource(uri)
            } else {
                retriever.setDataSource(context, parsedUri)
            }

            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
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
