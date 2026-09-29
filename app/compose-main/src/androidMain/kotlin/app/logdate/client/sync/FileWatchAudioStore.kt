package app.logdate.client.sync

import android.content.Context
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.uuid.Uuid

/**
 * Stores watch audio in the phone's private audio directory, next to phone recordings, and keeps
 * metadata that arrived ahead of its audio in a separate directory the audio scan never sees.
 *
 * The audio file appears at [audioPath] only when complete: bytes land in a staging file that is
 * synced to disk and renamed into place.
 */
class FileWatchAudioStore(
    private val audioDirectory: File,
    private val incomingDirectory: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : WatchAudioStore {
    constructor(context: Context) : this(
        audioDirectory = File(context.filesDir, "audio_notes"),
        incomingDirectory = File(context.filesDir, "watch_incoming"),
    )

    private val metadataSerializer = MapSerializer(String.serializer(), String.serializer())

    override fun audioPath(noteId: Uuid): String = audioFile(noteId).absolutePath

    override fun hasAudio(noteId: Uuid): Boolean = audioFile(noteId).isFile

    override suspend fun writeAudio(
        noteId: Uuid,
        source: InputStream,
    ): Boolean =
        withContext(ioDispatcher) {
            val staging = File(audioDirectory, "wear_$noteId.m4a.part")
            try {
                audioDirectory.mkdirs()
                FileOutputStream(staging).use { output ->
                    source.copyTo(output)
                    output.fd.sync()
                }
                if (staging.length() == 0L) {
                    staging.delete()
                    return@withContext false
                }
                Files.move(staging.toPath(), audioFile(noteId).toPath(), StandardCopyOption.ATOMIC_MOVE)
                true
            } catch (e: Exception) {
                Napier.w("Failed to store watch audio for note $noteId", e)
                staging.delete()
                false
            }
        }

    override suspend fun stashMetadata(
        noteId: Uuid,
        data: Map<String, String>,
    ) {
        withContext(ioDispatcher) {
            incomingDirectory.mkdirs()
            metadataFile(noteId).writeText(Json.encodeToString(metadataSerializer, data))
        }
    }

    override suspend fun stashedMetadata(noteId: Uuid): Map<String, String>? =
        withContext(ioDispatcher) {
            val file = metadataFile(noteId)
            if (!file.isFile) return@withContext null
            try {
                Json.decodeFromString(metadataSerializer, file.readText())
            } catch (e: Exception) {
                Napier.w("Discarding unreadable watch metadata for note $noteId", e)
                null
            }
        }

    override suspend fun clearMetadata(noteId: Uuid) {
        withContext(ioDispatcher) { metadataFile(noteId).delete() }
    }

    override suspend fun discard(noteId: Uuid) {
        withContext(ioDispatcher) {
            audioFile(noteId).delete()
            metadataFile(noteId).delete()
        }
    }

    override suspend fun markDeleted(noteId: Uuid) {
        withContext(ioDispatcher) {
            incomingDirectory.mkdirs()
            deletionMarker(noteId).writeText("")
        }
    }

    override suspend fun isDeleted(noteId: Uuid): Boolean = withContext(ioDispatcher) { deletionMarker(noteId).isFile }

    private fun audioFile(noteId: Uuid): File = File(audioDirectory, "wear_$noteId.m4a")

    private fun metadataFile(noteId: Uuid): File = File(incomingDirectory, "$noteId.json")

    private fun deletionMarker(noteId: Uuid): File = File(incomingDirectory, "$noteId.deleted")
}
