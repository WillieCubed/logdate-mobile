package app.logdate.client.media.audio.transcription

import kotlinx.coroutines.delay

/** Retries a transcript attachment without blocking realtime result delivery. */
class TranscriptionPersistenceRetrier(
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    private val delayMillis: (attempt: Int) -> Long = ::defaultDelayMillis,
    private val sleep: suspend (millis: Long) -> Unit = { delay(it) },
) {
    init {
        require(maxAttempts > 0) { "maxAttempts must be positive" }
    }

    suspend fun persist(operation: suspend () -> Boolean): Boolean {
        repeat(maxAttempts) { index ->
            if (operation()) return true
            if (index < maxAttempts - 1) {
                sleep(delayMillis(index + 1))
            }
        }
        return false
    }

    companion object {
        private const val DEFAULT_MAX_ATTEMPTS = 10
        private const val MAX_DELAY_MILLIS = 2_000L

        private fun defaultDelayMillis(attempt: Int): Long = exponentialDelay(attempt).coerceAtMost(MAX_DELAY_MILLIS)

        private fun exponentialDelay(attempt: Int): Long = 100L shl (attempt - 1).coerceAtMost(5)
    }
}
