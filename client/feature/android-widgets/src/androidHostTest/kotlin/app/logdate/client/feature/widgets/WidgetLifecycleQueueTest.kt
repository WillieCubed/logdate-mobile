package app.logdate.client.feature.widgets

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WidgetLifecycleQueueTest {
    @Test
    fun `empty dequeue lets Android finish without canceling a concurrently accepted job`() =
        runBlocking {
            val queue = ArrayDeque(listOf("first"))
            val applied = mutableListOf<String>()
            var upcomingJobRetained = false
            runWidgetLifecycleQueue(
                prepare = {},
                dequeue = {
                    queue.removeFirstOrNull().also {
                        if (it == null) upcomingJobRetained = true
                    }
                },
                process = { applied += it },
                complete = {},
                retry = { upcomingJobRetained = false },
                failed = { throw it },
            )
            assertEquals(listOf("first"), applied)
            assertTrue(upcomingJobRetained, "A stale jobFinished call can cancel the newly accepted work")
        }

    @Test
    fun `retained inbox is reconciled even when the OS has no remaining work items`() =
        runBlocking {
            val accepted = mutableListOf("retained restore mapping")
            val applied = mutableListOf<String>()
            runWidgetLifecycleQueue<String>(
                prepare = {
                    applied.addAll(accepted)
                    accepted.clear()
                },
                dequeue = { null },
                process = { error("There is no OS payload") },
                complete = { error("There is no OS payload to complete") },
                retry = { error("A drained queue belongs to Android") },
                failed = { throw it },
            )
            assertEquals(listOf("retained restore mapping"), applied)
            assertTrue(accepted.isEmpty())
        }

    @Test
    fun `failed reconciliation retains the unfinished OS payload and requests retry`() =
        runBlocking {
            val order = mutableListOf<String>()
            val failure = IOException("Settings persistence unavailable")
            runWidgetLifecycleQueue(
                prepare = {},
                dequeue = { "mapping" },
                process = { throw failure },
                complete = { order += "completed" },
                retry = { order += "retry" },
                failed = {
                    assertEquals(failure, it)
                    order += "failed"
                },
            )
            assertEquals(listOf("failed", "retry"), order)
        }

    @Test
    fun `incomplete native delivery requests retry before touching queued work`() =
        runBlocking {
            var retried = false
            runWidgetLifecycleQueue<String>(
                prepare = { throw IOException("Native delivery has not completed") },
                dequeue = { error("The queue must remain retained") },
                process = { error("No payload can be accepted yet") },
                complete = { error("No payload can be completed yet") },
                retry = { retried = true },
                failed = {},
            )
            assertTrue(retried)
        }

    @Test
    fun `system cancellation does not finish or retry a job whose stop callback owns it`() =
        runBlocking<Unit> {
            val processing = CompletableDeferred<Unit>()
            val job =
                async(start = CoroutineStart.UNDISPATCHED) {
                    runWidgetLifecycleQueue(
                        prepare = {},
                        dequeue = { "mapping" },
                        process = {
                            processing.complete(Unit)
                            awaitCancellation()
                        },
                        complete = { error("Canceled work must remain queued") },
                        retry = { error("onStopJob owns rescheduling") },
                        failed = { error("Cancellation is not a processing failure") },
                    )
                }
            withTimeout(1_000) { processing.await() }
            job.cancelAndJoin()
            assertTrue(job.isCancelled)
        }

    @Test
    fun `an operation deadline requests retry while the enclosing Android job remains active`() =
        runBlocking {
            val order = mutableListOf<String>()
            runWidgetLifecycleQueue(
                prepare = {},
                dequeue = { "mapping" },
                process = { withTimeout(1) { awaitCancellation() } },
                complete = { order += "completed" },
                retry = { order += "retry" },
                failed = {
                    assertTrue(it is TimeoutCancellationException)
                    order += "failed"
                },
            )
            assertEquals(listOf("failed", "retry"), order)
        }
}
