package app.logdate.client.data.transcription

import app.logdate.client.database.dao.AudioNoteDao
import app.logdate.client.database.dao.TranscriptionDao
import app.logdate.client.database.entities.TranscriptionEntity
import app.logdate.client.media.audio.transcription.TranscriptionManager
import app.logdate.client.repository.transcription.TranscriptionData
import app.logdate.client.repository.transcription.TranscriptionRepository
import app.logdate.client.repository.transcription.TranscriptionStatus
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlin.uuid.Uuid
import app.logdate.client.database.entities.TranscriptionStatus as DbTranscriptionStatus

/**
 * Implementation of [TranscriptionRepository] that stores transcriptions in the local database.
 */
class OfflineFirstTranscriptionRepository(
    private val transcriptionDao: TranscriptionDao,
    private val audioNoteDao: AudioNoteDao,
    private val transcriptionManager: TranscriptionManager
) : TranscriptionRepository {

    override suspend fun requestTranscription(noteId: Uuid): Boolean {
        Napier.d("Requesting transcription for note $noteId")
        
        // Check if the note exists
        val note = try {
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
                    val updatedTranscription = existingTranscription.copy(
                        status = DbTranscriptionStatus.PENDING,
                        errorMessage = null,
                        lastUpdated = now
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
        val transcription = TranscriptionEntity(
            noteId = noteId,
            text = null,
            status = DbTranscriptionStatus.PENDING,
            created = now,
            lastUpdated = now
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

    override suspend fun getTranscription(noteId: Uuid): TranscriptionData? {
        return transcriptionDao.getTranscriptionByNoteId(noteId)?.toTranscriptionData()
    }

    override fun observeTranscription(noteId: Uuid): Flow<TranscriptionData?> {
        return transcriptionDao.observeTranscriptionByNoteId(noteId)
            .map { it?.toTranscriptionData() }
    }

    override suspend fun getPendingTranscriptions(): List<TranscriptionData> {
        val pendingEntities = transcriptionDao.getTranscriptionsByStatus(DbTranscriptionStatus.PENDING)
        return pendingEntities.map { it.toTranscriptionData() }
    }

    override suspend fun updateTranscription(
        noteId: Uuid, 
        text: String?, 
        status: TranscriptionStatus,
        errorMessage: String?
    ): Boolean {
        Napier.d("Updating transcription for note $noteId to status $status")
        
        val transcription = transcriptionDao.getTranscriptionByNoteId(noteId)
        if (transcription == null) {
            Napier.e("Cannot update transcription: No transcription found for note $noteId")
            return false
        }
        
        val now = Clock.System.now()
        val dbStatus = when (status) {
            TranscriptionStatus.PENDING -> DbTranscriptionStatus.PENDING
            TranscriptionStatus.IN_PROGRESS -> DbTranscriptionStatus.IN_PROGRESS
            TranscriptionStatus.COMPLETED -> DbTranscriptionStatus.COMPLETED
            TranscriptionStatus.FAILED -> DbTranscriptionStatus.FAILED
        }
        
        return try {
            val updated = transcription.copy(
                text = text,
                status = dbStatus,
                errorMessage = errorMessage,
                lastUpdated = now
            )
            transcriptionDao.updateTranscription(updated)
            true
        } catch (e: Exception) {
            Napier.e("Failed to update transcription", e)
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
    private fun TranscriptionEntity.toTranscriptionData(): TranscriptionData {
        return TranscriptionData(
            noteId = noteId,
            text = text,
            status = when (status) {
                DbTranscriptionStatus.PENDING -> TranscriptionStatus.PENDING
                DbTranscriptionStatus.IN_PROGRESS -> TranscriptionStatus.IN_PROGRESS
                DbTranscriptionStatus.COMPLETED -> TranscriptionStatus.COMPLETED
                DbTranscriptionStatus.FAILED -> TranscriptionStatus.FAILED
            },
            errorMessage = errorMessage,
            created = created,
            lastUpdated = lastUpdated,
            id = id
        )
    }
}