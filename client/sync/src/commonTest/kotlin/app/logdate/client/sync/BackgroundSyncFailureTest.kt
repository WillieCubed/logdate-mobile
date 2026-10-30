package app.logdate.client.sync

import app.logdate.client.sync.test.InMemorySyncDeadLetterStore
import app.logdate.client.sync.test.testDefaultSyncManager
import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/** Background sync keeps running after a failure, reports it, and stops quietly when cancelled. */
class BackgroundSyncFailureTest {
    @Test
    fun `a failed debounced sync leaves later triggers running`() =
        runTest {
            var runs = 0
            val debouncer =
                SyncDebouncer(backgroundScope, WINDOW) {
                    runs++
                    if (runs == 1) error("first sync failed")
                }
            runCurrent()

            debouncer.trigger()
            advanceTimeBy(WINDOW * 2)
            debouncer.trigger()
            advanceTimeBy(WINDOW * 2)

            assertEquals(2, runs)
        }

    @Test
    fun `cancelling a debounced sync is not reported as a failure`() =
        runTest {
            val warnings = captureWarnings()
            try {
                val owner = Job()
                val started = CompletableDeferred<Unit>()
                val debouncer =
                    SyncDebouncer(CoroutineScope(StandardTestDispatcher(testScheduler) + owner), WINDOW) {
                        started.complete(Unit)
                        awaitCancellation()
                    }
                runCurrent()
                debouncer.trigger()
                advanceTimeBy(WINDOW * 2)
                assertTrue(started.isCompleted)

                owner.cancel()
                runCurrent()

                assertTrue(warnings.none { it.contains("SyncDebouncer action failed") }, "Cancellation was reported as $warnings")
            } finally {
                Napier.takeLogarithm()
            }
        }

    @Test
    fun `a failed background sync leaves the sync scope able to run the next one`() =
        runTest {
            val scope = backgroundSyncScope(StandardTestDispatcher(testScheduler))
            scope.launch(CoroutineExceptionHandler { _, _ -> }) { error("background sync failed") }
            runCurrent()

            assertTrue(scope.isActive)
            scope.cancel()
        }

    @Test
    fun `a failed background sync is reported as the last sync error`() =
        runTest {
            val failure = IllegalStateException("retry state unreadable")
            val deadLetters =
                object : app.logdate.client.sync.metadata.SyncDeadLetterStore by InMemorySyncDeadLetterStore() {
                    override suspend fun list() = throw failure
                }
            val manager = testDefaultSyncManager(deadLetterStore = deadLetters, syncScope = backgroundScope)

            manager.sync(startNow = true)
            runCurrent()

            val error = assertNotNull(manager.getLastSyncError())
            assertSame(failure, error.cause)
        }

    private fun captureWarnings(): MutableList<String> {
        val messages = mutableListOf<String>()
        Napier.base(
            object : Antilog() {
                override fun performLog(
                    priority: LogLevel,
                    tag: String?,
                    throwable: Throwable?,
                    message: String?,
                ) {
                    if (priority >= LogLevel.WARNING) messages += message.orEmpty()
                }
            },
        )
        return messages
    }

    private companion object {
        val WINDOW = 10.milliseconds
    }
}
