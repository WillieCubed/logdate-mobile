package app.logdate.client.media.audio

import app.logdate.client.media.audio.transcription.TranscriptionPersistenceRetrier
import app.logdate.client.media.audio.transcription.TranscriptionResult
import app.logdate.client.media.audio.transcription.toTranscriptDocument
import app.logdate.client.repository.transcription.TranscriptDocument
import app.logdate.client.repository.transcription.TranscriptDocumentStatus
import app.logdate.client.repository.transcription.TranscriptSource
import app.logdate.client.repository.transcription.TranscriptionRepository
import app.logdate.client.repository.transcription.TranscriptionStatus
import app.logdate.client.repository.transcription.TranscriptionWorkToken
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.Uuid

internal class RecordingTranscriptWriter(
    private val transcriptionRepository: TranscriptionRepository,
) {
    private val persistenceRetrier = TranscriptionPersistenceRetrier()

    suspend fun begin(
        noteId: Uuid,
        audioUri: String,
    ): RecordingTranscriptSession = RecordingTranscriptSession(transcriptionRepository.captureRecordingTranscript(noteId, audioUri))

    suspend fun persist(
        result: TranscriptionResult.Success,
        session: RecordingTranscriptSession,
    ): Boolean {
        if (result.text.isBlank() && !result.isFinal) return true
        val status =
            if (result.isFinal && !result.isRefining) {
                TranscriptionStatus.COMPLETED
            } else {
                TranscriptionStatus.IN_PROGRESS
            }
        val persisted =
            persistenceRetrier.persist {
                try {
                    session.mutex.withLock {
                        val work = session.work ?: return@withLock true
                        session.work = transcriptionRepository.persistRecordingTranscript(work, document(result, status), status)
                        true
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Napier.e("Recording transcript persistence attempt failed", e)
                    false
                }
            }
        return persisted
    }

    private fun document(
        result: TranscriptionResult.Success,
        status: TranscriptionStatus,
    ): TranscriptDocument {
        val source = if (result.isRefining) TranscriptSource.LOCAL_REFINEMENT else TranscriptSource.LOCAL_LIVE
        val documentStatus =
            if (status ==
                TranscriptionStatus.COMPLETED
            ) {
                TranscriptDocumentStatus.FINAL
            } else {
                TranscriptDocumentStatus.REFINING
            }
        return result.timedTranscript?.toTranscriptDocument(documentStatus, source)
            ?: TranscriptDocument.fromPlainText(result.text, documentStatus, source)
    }
}

internal class RecordingTranscriptSession(
    var work: TranscriptionWorkToken?,
) {
    val mutex = Mutex()
}
