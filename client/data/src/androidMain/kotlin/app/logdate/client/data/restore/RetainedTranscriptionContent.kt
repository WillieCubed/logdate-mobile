package app.logdate.client.data.restore

import app.logdate.client.database.entities.AudioNoteEntity
import app.logdate.client.database.entities.TranscriptionEntity
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Stable complete row fingerprints; identifiers and media bindings remain explicit inputs. */
internal fun RetainedTranscriptionSnapshot.contentFingerprint(): String {
    val row = row

    fun fields(values: Map<String, String?>) = JsonObject(values.mapValues { JsonPrimitive(it.value) })
    return JsonObject(
        mapOf(
            "row" to
                fields(
                    linkedMapOf(
                        "id" to row.id.toString(),
                        "noteId" to row.noteId.toString(),
                        "text" to row.text,
                        "documentJson" to row.documentJson,
                        "language" to row.language,
                        "source" to row.source,
                        "modelId" to row.modelId,
                        "revision" to row.revision.toString(),
                        "mediaUri" to row.mediaUri,
                        "mediaDurationMs" to row.mediaDurationMs?.toString(),
                        "isCloudEnhanced" to row.isCloudEnhanced.toString(),
                        "speakerCount" to row.speakerCount.toString(),
                        "status" to row.status.name,
                        "errorMessage" to row.errorMessage,
                        "created" to row.created.toString(),
                        "lastUpdated" to row.lastUpdated.toString(),
                    ),
                ),
            "segments" to
                JsonArray(
                    segments.sortedBy { it.segmentId }.map {
                        fields(
                            linkedMapOf(
                                "noteId" to it.noteId.toString(),
                                "segmentId" to it.segmentId,
                                "text" to it.text,
                                "startMs" to it.startMs.toString(),
                                "endMs" to it.endMs.toString(),
                                "speakerId" to it.speakerId,
                                "confidence" to it.confidence?.toString(),
                                "source" to it.source,
                                "isFinal" to it.isFinal.toString(),
                                "revision" to it.revision.toString(),
                            ),
                        )
                    },
                ),
        ),
    ).toString()
}

internal fun TranscriptionEntity.matchesRetainedAudio(note: AudioNoteEntity): Boolean {
    val transcriptMedia = mediaUri
    return (transcriptMedia == null || transcriptMedia.removePrefix("file://") == note.contentUri.removePrefix("file://")) &&
        (mediaDurationMs == null || mediaDurationMs == note.durationMs)
}

internal fun RetainedTranscriptionSnapshot.rebound(
    note: AudioNoteEntity,
    rowId: kotlin.uuid.Uuid,
): RetainedTranscriptionSnapshot =
    copy(
        row = row.copy(id = rowId, noteId = note.uid, mediaUri = if (row.mediaUri == null) null else note.contentUri),
        segments = segments.map { it.copy(noteId = note.uid) },
    )
