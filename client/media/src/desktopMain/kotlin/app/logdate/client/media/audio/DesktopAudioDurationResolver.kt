package app.logdate.client.media.audio

import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.sound.sampled.AudioSystem
import kotlin.math.roundToLong

class DesktopAudioDurationResolver : AudioDurationResolver {
    override suspend fun resolveDurationMs(uri: String): Long? =
        withContext(Dispatchers.IO) {
            try {
                val path =
                    when {
                        uri.startsWith("file:///") -> uri.substring(7)
                        uri.startsWith("file:/") -> uri.substring(5)
                        else -> uri
                    }

                val file = File(path)
                if (!file.exists()) {
                    return@withContext null
                }

                AudioSystem.getAudioInputStream(file).use { stream ->
                    val format = stream.format
                    if (format.frameRate <= 0) {
                        return@withContext null
                    }

                    val durationSeconds = stream.frameLength / format.frameRate
                    (durationSeconds * 1000).roundToLong()
                }
            } catch (e: Exception) {
                Napier.e("Failed to resolve audio duration for $uri", e)
                null
            }
        }
}
