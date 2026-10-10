package app.logdate.client.sync

import app.logdate.client.sync.datalayer.AudioFileDigest
import app.logdate.client.sync.datalayer.copyToDigesting
import kotlinx.coroutines.CompletableDeferred
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Records the acknowledgements the phone sends the watch. */
internal class RecordingAcknowledger : WatchNoteAcknowledger {
    val acknowledged = mutableListOf<Uuid>()
    val versions = mutableListOf<Instant>()

    override suspend fun acknowledge(
        noteId: Uuid,
        lastUpdated: Instant,
    ) {
        acknowledged += noteId
        versions += lastUpdated
    }
}

/** A [WatchAudioStore] in memory, with switches for a failed or a delayed write. */
internal class InMemoryWatchAudioStore : WatchAudioStore {
    private val audio = mutableMapOf<Uuid, ByteArray>()
    private val metadata = mutableMapOf<Uuid, Map<String, String>>()
    private val deleted = mutableSetOf<Uuid>()
    var failNextWrite = false
    var writeGate: CompletableDeferred<Unit>? = null
    var writes = 0

    fun audioText(noteId: Uuid): String? = audio[noteId]?.decodeToString()

    override fun audioPath(noteId: Uuid): String = "/phone/audio_notes/wear_$noteId.m4a"

    override fun hasAudio(noteId: Uuid): Boolean = noteId in audio

    override suspend fun writeAudio(
        noteId: Uuid,
        source: InputStream,
    ): Boolean {
        writeGate?.await()
        writes++
        if (failNextWrite) {
            failNextWrite = false
            return false
        }
        audio[noteId] = source.readBytes()
        return true
    }

    override suspend fun storedDigest(noteId: Uuid): AudioFileDigest? =
        audio[noteId]?.let { bytes -> ByteArrayInputStream(bytes).copyToDigesting(ByteArrayOutputStream()) }

    override suspend fun stashMetadata(
        noteId: Uuid,
        data: Map<String, String>,
    ) {
        metadata[noteId] = data
    }

    override suspend fun stashedMetadata(noteId: Uuid): Map<String, String>? = metadata[noteId]

    override suspend fun clearMetadata(noteId: Uuid) {
        metadata.remove(noteId)
    }

    override suspend fun discard(noteId: Uuid) {
        audio.remove(noteId)
        metadata.remove(noteId)
    }

    override suspend fun markDeleted(noteId: Uuid) {
        deleted += noteId
    }

    override suspend fun isDeleted(noteId: Uuid): Boolean = noteId in deleted
}
