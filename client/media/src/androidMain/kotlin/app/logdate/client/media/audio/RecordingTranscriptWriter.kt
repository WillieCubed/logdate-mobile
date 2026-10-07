package app.logdate.client.media.audio

import app.logdate.client.media.audio.transcription.TranscriptionPersistenceRetrier
import app.logdate.client.media.audio.transcription.TranscriptionResult
import app.logdate.client.media.audio.transcription.toTranscriptDocument
import app.logdate.client.repository.transcription.TranscriptDocumentStatus
import app.logdate.client.repository.transcription.TranscriptSource
import app.logdate.client.repository.transcription.TranscriptionRepository
import app.logdate.client.repository.transcription.TranscriptionStatus
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlin.uuid.Uuid

internal class RecordingTranscriptWriter(
    private val transcriptionRepository: TranscriptionRepository,
) {
    private val persistenceRetrier = TranscriptionPersistenceRetrier()

    suspend fun persist(
        result: TranscriptionResult.Success,
        noteId: Uuid,
    ): Boolean {
        if (result.text.isBlank()) return true
        val status =
            if (result.isFinal && !result.isRefining) {
                TranscriptionStatus.COMPLETED
            } else {
                TranscriptionStatus.IN_PROGRESS
            }
        val persisted =
            persistenceRetrier.persist {
                try {
                    writeTranscript(noteId, result, status)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Napier.e("Transcript persistence attempt failed for $noteId", e)
                    false
                }
            }
        return persisted
    }

    private suspend fun writeTranscript(
        noteId: Uuid,
        result: TranscriptionResult.Success,
        status: TranscriptionStatus,
    ): Boolean {
        val timedTranscript = result.timedTranscript
        return if (timedTranscript != null) {
            transcriptionRepository.updateTranscriptDocument(
                noteId = noteId,
                document =
                    timedTranscript.toTranscriptDocument(
                        status =
                            if (status == TranscriptionStatus.COMPLETED) {
                                TranscriptDocumentStatus.FINAL
                            } else {
                                TranscriptDocumentStatus.REFINING
                            },
                        source =
                            if (result.isRefining) {
                                TranscriptSource.LOCAL_REFINEMENT
                            } else {
                                TranscriptSource.LOCAL_LIVE
                            },
                    ),
                status = status,
            )
        } else {
            transcriptionRepository.updateTranscription(
                noteId = noteId,
                text = result.text,
                status = status,
            )
        }
    }
}
