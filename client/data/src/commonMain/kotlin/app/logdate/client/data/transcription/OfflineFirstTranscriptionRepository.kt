package app.logdate.client.data.transcription

import app.logdate.client.database.dao.AudioNoteDao
import app.logdate.client.database.dao.TranscriptionDao
import app.logdate.client.database.entities.TranscriptionEntity
import app.logdate.client.database.entities.TranscriptionSegmentEntity
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
import kotlin.uuid.Uuid

private typealias DbTranscriptionStatus = app.logdate.client.database.entities.TranscriptionStatus

/**
 * Implementation of [TranscriptionRepository] that stores transcriptions in the local database.
 */
class OfflineFirstTranscriptionRepository(
    private val transcriptionDao: TranscriptionDao,
    private val audioNoteDao: AudioNoteDao,
    private val transcriptionManager: TranscriptionManager,
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
            } catch (e: Exception) {
                Napier.e("Cannot request transcription: Note $noteId does not exist", e)
                return false
            }

        // Check if a transcription already exists
        val existingTranscription = transcriptionDao.getTranscriptionByNoteId(noteId)
        if (existingTranscription != null) {
            Napier.d("Transcription already exists for note $noteId with status ${existingTranscription.status}")
            // If it's already completed or in progress, don't request again
            return when (existingTranscription.status) {
                DbTranscriptionStatus.COMPLETED -> true
                DbTranscriptionStatus.IN_PROGRESS -> true
                DbTranscriptionStatus.PENDING -> {
                    // Re-queue if it's pending
                    val audioUri = note.contentUri
                    transcriptionManager.enqueueTranscription(noteId, audioUri)
                    true
                }
                DbTranscriptionStatus.FAILED -> {
                    // Create a new transcription request if previous one failed
                    val now = Clock.System.now()
                    val updatedTranscription =
                        existingTranscription.copy(
                            status = DbTranscriptionStatus.PENDING,
                            errorMessage = null,
                            lastUpdated = now,
                        )
                    transcriptionDao.updateTranscription(updatedTranscription)
                    val audioUri = note.contentUri
                    transcriptionManager.enqueueTranscription(noteId, audioUri)
                    true
                }
            }
        }

        // Create a new transcription entry
        val now = Clock.System.now()
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
            transcriptionManager.enqueueTranscription(noteId, audioUri)
            return true
        } catch (e: Exception) {
            Napier.e("Failed to request transcription", e)
            return false
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

        val transcription =
            transcriptionDao.getTranscriptionByNoteId(noteId)
                ?: run {
                    // No row yet — create one if the note exists in the database.
                    // This happens when live transcription completes before the note's
                    // auto-save fires or before requestTranscription() is explicitly called.
                    val noteExists =
                        try {
                            audioNoteDao.getNoteOneOff(noteId)
                            true
                        } catch (e: Exception) {
                            false
                        }
                    if (!noteExists) {
                        Napier.w("Cannot persist transcript: note $noteId not in DB yet — will retry on next emission")
                        return false
                    }
                    val now = Clock.System.now()
                    transcriptionDao.insertTranscription(
                        TranscriptionEntity(
                            noteId = noteId,
                            text = text,
                            status = dbStatus,
                            created = now,
                            lastUpdated = now,
                        ),
                    )
                    transcriptionDao.replaceSegmentsForNote(
                        noteId = noteId,
                        segments = text.toLegacySegmentEntities(noteId),
                    )
                    return true
                }

        val now = Clock.System.now()
        return try {
            transcriptionDao.updateTranscription(
                transcription.copy(
                    text = text,
                    status = dbStatus,
                    errorMessage = errorMessage,
                    lastUpdated = now,
                ),
            )
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

        val dbStatus = status.toDbStatus()
        val documentJson = transcriptJson.encodeToString(document)
        val transcription =
            transcriptionDao.getTranscriptionByNoteId(noteId)
                ?: run {
                    val now = Clock.System.now()
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
                            created = now,
                            lastUpdated = now,
                        ),
                    )
                    return true
                }

        val now = Clock.System.now()
        return try {
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
                    lastUpdated = now,
                ),
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
