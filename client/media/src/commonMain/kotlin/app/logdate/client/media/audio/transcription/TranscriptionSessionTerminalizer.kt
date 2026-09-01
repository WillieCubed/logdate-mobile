package app.logdate.client.media.audio.transcription

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes one realtime transcription session's result lifecycle.
 *
 * Partial results remain an in-memory hot path. This class only prevents a
 * background coroutine from publishing progress after the session has already
 * succeeded, failed, or been cancelled.
 */
class TranscriptionSessionTerminalizer(
    private val emit: suspend (TranscriptionResult) -> Unit,
) {
    private val mutex = Mutex()
    private var active = false

    suspend fun begin(): TranscriptionStartResult =
        mutex.withLock {
            if (active) return@withLock TranscriptionStartResult.AlreadyRunning
            active = true
            emit(TranscriptionResult.InProgress)
            TranscriptionStartResult.Started
        }

    suspend fun progress(result: TranscriptionResult.Success): Boolean =
        mutex.withLock {
            if (!active) return@withLock false
            emit(result)
            true
        }

    suspend fun complete(result: TranscriptionResult.Success): Boolean =
        mutex.withLock {
            if (!active) return@withLock false
            require(result.isFinal && !result.isRefining) {
                "A terminal transcription success must be final and not refining"
            }
            active = false
            emit(result)
            true
        }

    suspend fun fail(reason: TranscriptionFailure): Boolean =
        mutex.withLock {
            if (!active) return@withLock false
            active = false
            emit(TranscriptionResult.Error(reason))
            true
        }

    suspend fun cancel(): Boolean =
        mutex.withLock {
            if (!active) return@withLock false
            active = false
            emit(TranscriptionResult.Cancelled)
            true
        }
}
