package app.logdate.feature.core.restore

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArchiveMediaRetryHandoffTest {
    @Test
    fun `a retry successor is durably enqueued before the current worker succeeds`() =
        runTest {
            var enqueued = false

            val handedOff = enqueueArchiveMediaRetry { enqueued = true }

            assertTrue(handedOff)
            assertTrue(enqueued)
        }

    @Test
    fun `a failed retry enqueue leaves the current worker retryable`() =
        runTest {
            val handedOff = enqueueArchiveMediaRetry { error("WorkManager persistence failed") }

            assertFalse(handedOff)
        }

    @Test
    fun `retry cancellation propagates to WorkManager`() =
        runTest {
            var cancelled = false
            try {
                enqueueArchiveMediaRetry { throw CancellationException("Worker stopped") }
            } catch (_: CancellationException) {
                cancelled = true
            }

            assertTrue(cancelled)
        }

    @Test
    fun `successive retry delay is capped`() {
        assertTrue(ArchiveRecoveryScheduler.retryDelayMillis(1) < ArchiveRecoveryScheduler.retryDelayMillis(2))
        assertTrue(ArchiveRecoveryScheduler.retryDelayMillis(50) <= 900_000L)
    }
}
