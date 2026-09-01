package app.logdate.client.media.audio.transcription

import app.logdate.client.repository.transcription.TranscriptionStatus
import kotlinx.coroutines.CancellationException
import kotlin.uuid.Uuid

internal enum class TranscriptionWorkOutcome {
    Success,
    Retry,
    Failure,
}

internal class TranscriptionWorkRunner(
    private val update: suspend (Uuid, String?, TranscriptionStatus, String?) -> Boolean,
    private val transcribe: suspend (String) -> TranscriptionResult,
) {
    suspend fun run(
        noteId: Uuid,
        audioUri: String,
    ): TranscriptionWorkOutcome {
        return try {
            if (!update(noteId, null, TranscriptionStatus.IN_PROGRESS, null)) {
                return TranscriptionWorkOutcome.Retry
            }
            when (val result = transcribe(audioUri)) {
                is TranscriptionResult.Success -> {
                    if (result.text.isBlank()) {
                        persistFailure(noteId, TranscriptionFailure.NoSpeechDetected)
                    } else if (
                        update(
                            noteId,
                            result.text,
                            TranscriptionStatus.COMPLETED,
                            null,
                        )
                    ) {
                        TranscriptionWorkOutcome.Success
                    } else {
                        TranscriptionWorkOutcome.Retry
                    }
                }
                is TranscriptionResult.Error -> persistFailure(noteId, result.reason)
                is TranscriptionResult.InProgress,
                TranscriptionResult.Cancelled,
                -> TranscriptionWorkOutcome.Retry
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            runCatching { persistFailure(noteId, TranscriptionFailure.Unknown) }
                .getOrDefault(TranscriptionWorkOutcome.Retry)
        }
    }

    private suspend fun persistFailure(
        noteId: Uuid,
        failure: TranscriptionFailure,
    ): TranscriptionWorkOutcome =
        if (
            update(
                noteId,
                null,
                TranscriptionStatus.FAILED,
                failure.toString(),
            )
        ) {
            TranscriptionWorkOutcome.Failure
        } else {
            TranscriptionWorkOutcome.Retry
        }
}
