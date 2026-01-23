package app.logdate.client.media.audio

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.uuid.Uuid

/**
 * Android implementation of [AudioStorage] that stores recordings in app-private files.
 */
class AndroidAudioStorage(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AudioStorage {
    override suspend fun createRecordingTarget(
        noteId: Uuid?,
        extension: String,
    ): AudioRecordingTarget = withContext(ioDispatcher) {
        val safeExtension = extension.trimStart('.').ifBlank { "m4a" }
        val directory = File(context.filesDir, "audio_notes").apply {
            if (!exists()) {
                mkdirs()
            }
        }

        val baseName = noteId?.toString() ?: "recording_${System.currentTimeMillis()}"
        val targetFile = File(directory, "$baseName.$safeExtension")

        if (targetFile.exists()) {
            targetFile.delete()
        }

        AudioRecordingTarget(noteId = noteId, path = targetFile.absolutePath)
    }
}
