package app.logdate.client.data.transcription

import app.logdate.client.database.dao.AudioNoteDao
import app.logdate.client.database.dao.TranscriptionDao
import app.logdate.client.database.entities.TranscriptionEntity
import app.logdate.client.database.entities.TranscriptionSegmentEntity
import app.logdate.client.media.audio.transcription.TranscriptionFailure
import app.logdate.client.media.audio.transcription.TranscriptionManager
import app.logdate.client.repository.transcription.TranscriptDocument
import app.logdate.client.repository.transcription.TranscriptSegment
import app.logdate.client.repository.transcription.TranscriptSource
import app.logdate.client.repository.transcription.TranscriptionData
import app.logdate.client.repository.transcription.TranscriptionRepository
import app.logdate.client.repository.transcription.TranscriptionStatus
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

private typealias DbTranscriptionStatus = app.logdate.client.database.entities.TranscriptionStatus

/**
 * Implementation of [TranscriptionRepository] that stores transcriptions in the local database.
 */
class OfflineFirstTranscriptionRepository(
    private val transcriptionDao: TranscriptionDao,
    private val audioNoteDao: AudioNoteDao,
    private val transcriptionManager: TranscriptionManager,
    private val now: () -> Instant = { Clock.System.now() },
    private val staleAfter: Duration = 10.minutes,
) : TranscriptionRepository {
    private val transcriptJson =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    override suspend fun requestTranscription(noteId: Uuid): Boolean {
        Napier.d("Requesting transcription for note $noteId")

        // Check if the note exists
        val note =
            try {
                audioNoteDao.getNoteOneOff(noteId)
            } catch (e: NoSuchElementException) {
                Napier.e("Cannot request transcription: Note $noteId does not exist", e)
                return false
            } catch (e: Exception) {
                Napier.e("Cannot request transcription: failed to read note $noteId", e)
                return false
            }

        // Check if a transcription already exists
        val existingTranscription =
            try {
                transcriptionDao.getTranscriptionByNoteId(noteId)
            } catch (e: Exception) {
                Napier.e("Cannot request transcription: failed to read existing state for note $noteId", e)
                return false
            }
        if (existingTranscription != null) {
            Napier.d("Transcription already exists for note $noteId with status ${existingTranscription.status}")
            // If it's already completed or in progress, don't request again
            return when (existingTranscription.status) {
                DbTranscriptionStatus.COMPLETED -> true
                DbTranscriptionStatus.IN_PROGRESS -> {
                    if (now() - existingTranscription.lastUpdated < staleAfter) {
                        true
                    } else {
                        Napier.w("Requeuing stale in-progress transcription for note $noteId")
                        requeue(existingTranscription, note.contentUri)
                    }
                }
                DbTranscriptionStatus.PENDING -> {
                    enqueueOrFail(existingTranscription, note.contentUri)
                }
                DbTranscriptionStatus.FAILED -> {
                    requeue(existingTranscription, note.contentUri)
                }
            }
        }

        // Create a new transcription entry
        val now = now()
        val transcription =
            TranscriptionEntity(
                noteId = noteId,
                text = null,
                status = DbTranscriptionStatus.PENDING,
                created = now,
                lastUpdated = now,
            )

        // Insert into database
        try {
            transcriptionDao.insertTranscription(transcription)
            // Enqueue the transcription job
            val audioUri = note.contentUri
            return enqueueOrFail(transcription, audioUri)
        } catch (e: Exception) {
            Napier.e("Failed to request transcription", e)
            return false
        }
    }

    private suspend fun requeue(
        transcription: TranscriptionEntity,
        audioUri: String,
    ): Boolean {
        return try {
            val pending =
                transcription.copy(
                    status = DbTranscriptionStatus.PENDING,
                    errorMessage = null,
                    lastUpdated = now(),
                )
            if (transcriptionDao.updateTranscription(pending) <= 0) {
                Napier.e("Failed to move transcription for ${transcription.noteId} back to PENDING")
                return false
            }
            enqueueOrFail(pending, audioUri)
        } catch (e: Exception) {
            Napier.e("Failed to requeue transcription for ${transcription.noteId}", e)
            return false
        }
    }

    private suspend fun enqueueOrFail(
        transcription: TranscriptionEntity,
        audioUri: String,
    ): Boolean {
        return try {
            if (transcriptionManager.enqueueTranscription(transcription.noteId, audioUri)) {
                return true
            }
            val failed =
                transcription.copy(
                    status = DbTranscriptionStatus.FAILED,
                    errorMessage = TranscriptionFailure.SchedulingError.toString(),
                    lastUpdated = now(),
                )
            val persisted = transcriptionDao.updateTranscription(failed) > 0
            if (!persisted) {
                Napier.e("Failed to persist scheduling failure for ${transcription.noteId}")
            }
            false
        } catch (e: Exception) {
            Napier.e("Failed to schedule transcription for ${transcription.noteId}", e)
            false
        }
    }

    override suspend fun getTranscription(noteId: Uuid): TranscriptionData? =
        transcriptionDao.getTranscriptionByNoteId(noteId)?.toTranscriptionData()

    override fun observeTranscription(noteId: Uuid): Flow<TranscriptionData?> =
        transcriptionDao
            .observeTranscriptionByNoteId(noteId)
            .map { it?.toTranscriptionData() }

    override suspend fun getPendingTranscriptions(): List<TranscriptionData> {
        val pendingEntities = transcriptionDao.getTranscriptionsByStatus(DbTranscriptionStatus.PENDING)
        return pendingEntities.map { it.toTranscriptionData() }
    }

    override suspend fun updateTranscription(
        noteId: Uuid,
        text: String?,
        status: TranscriptionStatus,
        errorMessage: String?,
    ): Boolean {
        Napier.d("Updating transcription for note $noteId to status $status")

        val dbStatus =
            when (status) {
                TranscriptionStatus.PENDING -> DbTranscriptionStatus.PENDING
                TranscriptionStatus.IN_PROGRESS -> DbTranscriptionStatus.IN_PROGRESS
                TranscriptionStatus.COMPLETED -> DbTranscriptionStatus.COMPLETED
                TranscriptionStatus.FAILED -> DbTranscriptionStatus.FAILED
            }

        return try {
            val transcription = transcriptionDao.getTranscriptionByNoteId(noteId)
            val timestamp = now()
            if (transcription == null) {
                // Live transcription can finish before note auto-save. Returning
                // false keeps the in-memory persistence retrier active until the
                // note exists instead of losing the transcript.
                try {
                    audioNoteDao.getNoteOneOff(noteId)
                } catch (e: Exception) {
                    Napier.w("Cannot persist transcript: note $noteId is not available yet", e)
                    return false
                }
                transcriptionDao.insertTranscription(
                    TranscriptionEntity(
                        noteId = noteId,
                        text = text,
                        status = dbStatus,
                        errorMessage = errorMessage,
                        created = timestamp,
                        lastUpdated = timestamp,
                    ),
                )
            } else {
                val updated =
                    transcriptionDao.updateTranscription(
                        transcription.copy(
                            text = text,
                            status = dbStatus,
                            errorMessage = errorMessage,
                            lastUpdated = timestamp,
                        ),
                    )
                if (updated <= 0) {
                    Napier.e("Transcription update touched no row for note $noteId")
                    return false
                }
            }
            transcriptionDao.replaceSegmentsForNote(
                noteId = noteId,
                segments = text.toLegacySegmentEntities(noteId),
            )
            true
        } catch (e: Exception) {
            Napier.e("Failed to update transcription", e)
            false
        }
    }

    override suspend fun updateTranscriptDocument(
        noteId: Uuid,
        document: TranscriptDocument,
        status: TranscriptionStatus,
        errorMessage: String?,
    ): Boolean {
        Napier.d("Updating structured transcription for note $noteId to status $status")

        return try {
            val dbStatus = status.toDbStatus()
            val documentJson = transcriptJson.encodeToString(document)
            val transcription = transcriptionDao.getTranscriptionByNoteId(noteId)
            val timestamp = now()
            if (transcription == null) {
                try {
                    audioNoteDao.getNoteOneOff(noteId)
                } catch (e: Exception) {
                    Napier.w("Cannot persist structured transcript: note $noteId is not available yet", e)
                    return false
                }
                transcriptionDao.insertTranscription(
                    TranscriptionEntity(
                        noteId = noteId,
                        text = document.plainText,
                        documentJson = documentJson,
                        language = document.language,
                        source = document.primarySourceName(),
                        revision = document.revision,
                        isCloudEnhanced = document.isCloudEnhanced,
                        speakerCount = document.speakers.size,
                        status = dbStatus,
                        errorMessage = errorMessage,
                        created = timestamp,
                        lastUpdated = timestamp,
                    ),
                )
            } else {
                val updated =
                    transcriptionDao.updateTranscription(
                        transcription.copy(
                            text = document.plainText,
                            documentJson = documentJson,
                            language = document.language,
                            source = document.primarySourceName(),
                            revision = document.revision,
                            isCloudEnhanced = document.isCloudEnhanced,
                            speakerCount = document.speakers.size,
                            status = dbStatus,
                            errorMessage = errorMessage,
                            lastUpdated = timestamp,
                        ),
                    )
                if (updated <= 0) {
                    Napier.e("Structured transcription update touched no row for note $noteId")
                    return false
                }
            }
            transcriptionDao.replaceSegmentsForNote(
                noteId = noteId,
                segments = document.toSegmentEntities(noteId),
            )
            true
        } catch (e: Exception) {
            Napier.e("Failed to update structured transcription", e)
            false
        }
    }

    override suspend fun deleteTranscription(noteId: Uuid): Boolean {
        Napier.d("Deleting transcription for note $noteId")

        return try {
            val deleted = transcriptionDao.deleteTranscriptionByNoteId(noteId)
            deleted > 0
        } catch (e: Exception) {
            Napier.e("Failed to delete transcription", e)
            false
        }
    }

    /**
     * Converts a [TranscriptionEntity] to a [TranscriptionData] object.
     */
    private fun TranscriptionEntity.toTranscriptionData(): TranscriptionData =
        TranscriptionData(
            noteId = noteId,
            text = text,
            transcriptDocument = decodeTranscriptDocument(documentJson),
            status =
                when (status) {
                    DbTranscriptionStatus.PENDING -> TranscriptionStatus.PENDING
                    DbTranscriptionStatus.IN_PROGRESS -> TranscriptionStatus.IN_PROGRESS
                    DbTranscriptionStatus.COMPLETED -> TranscriptionStatus.COMPLETED
                    DbTranscriptionStatus.FAILED -> TranscriptionStatus.FAILED
                },
            language = language,
            source = source?.let { decodeTranscriptSource(it) },
            modelId = modelId,
            revision = revision,
            isCloudEnhanced = isCloudEnhanced,
            speakerCount = speakerCount,
            errorMessage = errorMessage,
            created = created,
            lastUpdated = lastUpdated,
            id = id,
        )

    private fun TranscriptionStatus.toDbStatus(): DbTranscriptionStatus =
        when (this) {
            TranscriptionStatus.PENDING -> DbTranscriptionStatus.PENDING
            TranscriptionStatus.IN_PROGRESS -> DbTranscriptionStatus.IN_PROGRESS
            TranscriptionStatus.COMPLETED -> DbTranscriptionStatus.COMPLETED
            TranscriptionStatus.FAILED -> DbTranscriptionStatus.FAILED
        }

    private fun decodeTranscriptDocument(documentJson: String?): TranscriptDocument? =
        documentJson?.let {
            try {
                transcriptJson.decodeFromString<TranscriptDocument>(it)
            } catch (e: Exception) {
                Napier.e("Failed to decode structured transcription document", e)
                null
            }
        }

    private fun decodeTranscriptSource(source: String): TranscriptSource? =
        try {
            TranscriptSource.valueOf(source)
        } catch (e: IllegalArgumentException) {
            Napier.w("Unknown transcript source: $source", e)
            null
        }

    private fun TranscriptDocument.primarySourceName(): String? = segments.maxByOrNull { it.source.ordinal }?.source?.name

    private fun String?.toLegacySegmentEntities(noteId: Uuid): List<TranscriptionSegmentEntity> =
        takeUnless { it.isNullOrBlank() }
            ?.let { TranscriptDocument.fromPlainText(it).toSegmentEntities(noteId) }
            .orEmpty()

    private fun TranscriptDocument.toSegmentEntities(noteId: Uuid): List<TranscriptionSegmentEntity> =
        segments.map { segment ->
            segment.toEntity(
                noteId = noteId,
                revision = revision,
            )
        }

    private fun TranscriptSegment.toEntity(
        noteId: Uuid,
        revision: Int,
    ): TranscriptionSegmentEntity =
        TranscriptionSegmentEntity(
            noteId = noteId,
            segmentId = segmentId,
            text = text,
            startMs = startMs,
            endMs = endMs,
            speakerId = speakerId,
            confidence = confidence,
            source = source.name,
            isFinal = isFinal,
            revision = revision,
        )

    private val TranscriptDocument.isCloudEnhanced: Boolean
        get() =
            segments.any {
                it.source == TranscriptSource.CLOUD_LIVE ||
                    it.source == TranscriptSource.CLOUD_REFINEMENT
            }
}
