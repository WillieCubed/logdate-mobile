package app.logdate.client.media.audio.transcription

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TranscriptionPersistenceRetrierTest {
    @Test
    fun `a transient persistence failure is retried without blocking on a real clock`() =
        runTest {
            var attempts = 0
            val delays = mutableListOf<Long>()
            val retrier =
                TranscriptionPersistenceRetrier(
                    maxAttempts = 3,
                    delayMillis = { attempt -> attempt * 100L },
                    sleep = { delays.add(it) },
                )

            val persisted =
                retrier.persist {
                    attempts += 1
                    attempts == 2
                }

            assertTrue(persisted)
            assertTrue(attempts == 2)
            assertTrue(delays == listOf(100L))
        }

    @Test
    fun `persistence exhaustion returns false after the configured number of attempts`() =
        runTest {
            var attempts = 0
            val retrier =
                TranscriptionPersistenceRetrier(
                    maxAttempts = 3,
                    delayMillis = { 0L },
                    sleep = {},
                )

            val persisted =
                retrier.persist {
                    attempts += 1
                    false
                }

            assertFalse(persisted)
            assertTrue(attempts == 3)
        }
}
