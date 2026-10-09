package app.logdate.client.data.transcription

import app.logdate.client.database.entities.AudioNoteEntity
import app.logdate.client.database.entities.TranscriptionEntity
import app.logdate.client.database.entities.TranscriptionSegmentEntity
import app.logdate.client.repository.transcription.TranscriptDocument
import app.logdate.client.repository.transcription.TranscriptDocumentStatus
import app.logdate.client.repository.transcription.TranscriptSource
import app.logdate.client.repository.transcription.TranscriptionData
import app.logdate.client.repository.transcription.TranscriptionStatus
import io.github.aakira.napier.Napier
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Instant
import kotlin.uuid.Uuid

private typealias DbStatus = app.logdate.client.database.entities.TranscriptionStatus

private val transcriptJson =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

internal fun TranscriptionEntity.toTranscriptionData(): TranscriptionData {
    val document =
        decodedDocument() ?: if (documentJson == null && status == DbStatus.COMPLETED && !text.isNullOrBlank()) {
            TranscriptDocument.fromPlainText(text.orEmpty()).copy(revision = revision)
        } else {
            null
        }
    return TranscriptionData(
        noteId = noteId,
        text = text,
        transcriptDocument = document,
        status = TranscriptionStatus.valueOf(status.name),
        language = language,
        source = source?.let { name -> TranscriptSource.entries.firstOrNull { it.name == name } },
        modelId = modelId,
        revision = revision,
        isCloudEnhanced = isCloudEnhanced,
        speakerCount = speakerCount,
        errorMessage = errorMessage,
        created = created,
        lastUpdated = lastUpdated,
        id = id,
    )
}

internal fun TranscriptionEntity.decodedDocument(): TranscriptDocument? =
    documentJson?.let {
        try {
            transcriptJson.decodeFromString<TranscriptDocument>(it)
        } catch (_: Exception) {
            Napier.e("Failed to decode transcript document")
            null
        }
    }

internal fun TranscriptionEntity.hasValidFinalDocument(): Boolean =
    status == DbStatus.COMPLETED &&
        toTranscriptionData().transcriptDocument?.let { document ->
            document.isFinal &&
                document.revision >= 0 &&
                document.language.isNotBlank() &&
                document.segments.all { segment -> segment.startMs >= 0 && segment.words.all { it.startMs >= 0 } }
        } == true

internal fun TranscriptionEntity.matchesMedia(note: AudioNoteEntity): Boolean =
    (mediaUri == null || mediaUri == note.contentUri) && (mediaDurationMs == null || mediaDurationMs == note.durationMs)

internal fun TranscriptionStatus.toDbStatus(): DbStatus = DbStatus.valueOf(name)

internal fun TranscriptionStatus.toDocumentStatus(): TranscriptDocumentStatus =
    when (this) {
        TranscriptionStatus.PENDING, TranscriptionStatus.IN_PROGRESS -> TranscriptDocumentStatus.LISTENING
        TranscriptionStatus.COMPLETED -> TranscriptDocumentStatus.FINAL
        TranscriptionStatus.FAILED -> TranscriptDocumentStatus.FAILED
    }

internal fun TranscriptionEntity.withDocument(
    document: TranscriptDocument?,
    status: DbStatus,
    errorMessage: String?,
    timestamp: Instant,
): TranscriptionEntity =
    if (document == null) {
        copy(status = status, errorMessage = errorMessage, lastUpdated = timestamp)
    } else {
        copy(
            text = document.plainText,
            documentJson = transcriptJson.encodeToString(document),
            language = document.language,
            source =
                document.segments
                    .maxByOrNull { it.source.ordinal }
                    ?.source
                    ?.name,
            modelId = document.engine?.modelId,
            revision = document.revision,
            isCloudEnhanced =
                document.segments.any {
                    it.source == TranscriptSource.CLOUD_LIVE || it.source == TranscriptSource.CLOUD_REFINEMENT
                },
            speakerCount = document.speakers.size,
            status = status,
            errorMessage = errorMessage,
            lastUpdated = timestamp,
        )
    }

internal fun TranscriptDocument.toSegmentEntities(noteId: Uuid): List<TranscriptionSegmentEntity> =
    segments.map { segment ->
        TranscriptionSegmentEntity(
            noteId = noteId,
            segmentId = segment.segmentId,
            text = segment.text,
            startMs = segment.startMs,
            endMs = segment.endMs,
            speakerId = segment.speakerId,
            confidence = segment.confidence,
            source = segment.source.name,
            isFinal = segment.isFinal,
            revision = revision,
        )
    }
