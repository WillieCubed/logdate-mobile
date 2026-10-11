package app.logdate.client.sync.v2.receipts

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.WatchNoteAcknowledger
import app.logdate.client.sync.datalayer.AudioFileDigest
import app.logdate.client.sync.datalayer.NoteDataMapper
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.RecordEnvelope
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.RecordOrigin
import app.logdate.client.sync.datalayer.v2.RecordPath
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.peer.delivery.EnvelopeDataMap
import app.logdate.client.sync.peer.delivery.NoteEnvelopeBody
import io.github.aakira.napier.Napier
import java.io.InputStream
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Turns the version 2 records a watch writes into phone notes. Notes and recordings go through the same
 * ingest as version 1 ([ingestMetadata], [ingestAudio]), so a recording is stored only once its size and
 * SHA-256 match; a text note is acknowledged here once stored, a recording by the ingest once complete.
 */
class WatchRecordIngestorV2(
    private val ingestMetadata: suspend (Map<String, String>) -> Unit,
    private val ingestAudio: suspend (Uuid, InputStream) -> Unit,
    private val notes: JournalNotesRepository,
    private val acknowledger: WatchNoteAcknowledger,
    private val rejection: RecordRejection,
    private val mapper: NoteDataMapper = NoteDataMapper(),
    private val receivedVersion: suspend (Uuid) -> Long? = { null },
    private val scoped: ScopedCaptureIngestion? = null,
) {
    suspend fun onRecord(
        sourceNodeId: String?,
        path: String,
        data: Map<String, String>,
    ) {
        scoped?.onRecord(sourceNodeId, path, data)
    }

    suspend fun onMedia(
        sourceNodeId: String?,
        path: String,
        input: InputStream,
    ) {
        scoped?.onMedia(sourceNodeId, path, input)
    }

    suspend fun onRecord(
        path: String,
        data: Map<String, String>,
    ) {
        val recordPath = WearPaths.parseWatchRecord(path)
        if (recordPath == null) {
            Napier.w("Ignoring a data item that is not a watch record: $path")
            return
        }
        val envelope =
            when (val decoded = EnvelopeDataMap.fromDataMap(data)) {
                is Decoded.Supported -> decoded.value
                is Decoded.Unsupported -> return refuse(recordPath, "it was written by a newer version")
                is Decoded.Malformed -> return refuse(recordPath, decoded.reason)
            }
        if (envelope.context != null) {
            Napier.w("A scoped capture requires a source-validated ingestion path")
            return
        }
        if (envelope.kind != recordPath.kind) return refuse(recordPath, "its kind does not match its path")
        if (envelope.origin != RecordOrigin.WATCH) return refuse(recordPath, "it was not captured on a watch")
        if (envelope.kind != RecordKind.NOTE) return refuse(recordPath, "this phone takes only notes this way")
        if (envelope.id != recordPath.id.toString()) return refuse(recordPath, "its id does not match its path")
        val note = readNote(envelope) ?: return refuse(recordPath, "its body is not a note")
        if (note.uid != recordPath.id ||
            note.lastUpdated.toEpochMilliseconds() != envelope.contentVersion ||
            note.syncVersion != envelope.syncVersion
        ) {
            return refuse(recordPath, "its body does not match its envelope")
        }
        if (note is JournalNote.Audio &&
            (
                envelope.media.size != 1 ||
                    !envelope.media
                        .single()
                        .mimeType
                        .startsWith("audio/")
            )
        ) {
            return refuse(recordPath, "its audio has no single integrity reference")
        }
        val received = receivedVersion(note.uid)
        if (received != null && received >= envelope.contentVersion && notes.getNoteById(note.uid) == null) {
            acknowledger.acknowledge(note.uid, Instant.fromEpochMilliseconds(received))
            return
        }
        ingestMetadata(dataMapFor(note, envelope))
        if (note !is JournalNote.Audio) acknowledgeIfStored(note)
    }

    suspend fun onMedia(
        path: String,
        input: InputStream,
    ) {
        val recordPath = WearPaths.parseRecordMedia(path)
        if (recordPath == null || recordPath.kind != RecordKind.NOTE || recordPath.linkId != null) {
            Napier.w("Ignoring a recording on an unsupported or unbound note channel: $path")
            return
        }
        ingestAudio(recordPath.id, input)
    }

    private fun readNote(envelope: RecordEnvelope): JournalNote? {
        val body = envelope.body ?: return null
        return try {
            NoteEnvelopeBody.decode(body, mapper)
        } catch (e: IllegalArgumentException) {
            Napier.e("A watch note's body could not be read", e)
            null
        }
    }

    private fun dataMapFor(
        note: JournalNote,
        envelope: RecordEnvelope,
    ): Map<String, String> {
        val map = mapper.toDataMap(note)
        val media = envelope.media.firstOrNull() ?: return map
        return NoteDataMapper.withAudioDigest(map, AudioFileDigest(sizeBytes = media.sizeBytes, sha256Hex = media.sha256))
    }

    private suspend fun acknowledgeIfStored(note: JournalNote) {
        val stored = notes.getNoteById(note.uid) ?: return
        acknowledger.acknowledge(note.uid, stored.lastUpdated)
    }

    private suspend fun refuse(
        path: RecordPath,
        why: String,
    ) {
        Napier.w("Refused a ${path.kind} record from the watch because $why")
        rejection.reject(path.kind, path.id.toString())
    }
}
