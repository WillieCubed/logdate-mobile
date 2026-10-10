@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.logdate.client.media.audio

import app.logdate.client.media.storage.MediaFileResolver
import app.logdate.client.media.storage.fileUrl
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVURLAsset
import platform.CoreMedia.CMTimeGetSeconds
import kotlin.math.roundToLong

class IosAudioDurationResolver(
    private val mediaFiles: MediaFileResolver,
) : AudioDurationResolver {
    override suspend fun resolveDurationMs(uri: String): Long? =
        withContext(Dispatchers.Default) {
            try {
                val url = mediaFiles.fileUrl(uri)

                if (url == null) {
                    Napier.e("Failed to resolve audio URL for $uri")
                    return@withContext null
                }

                val asset = AVURLAsset.URLAssetWithURL(url, null)
                val durationSeconds = CMTimeGetSeconds(asset.duration)
                if (durationSeconds.isNaN() || durationSeconds <= 0) {
                    return@withContext null
                }
                (durationSeconds * 1000).roundToLong()
            } catch (e: Exception) {
                Napier.e("Failed to resolve audio duration for $uri", e)
                null
            }
        }
}
