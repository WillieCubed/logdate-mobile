package app.logdate.client.feature.widgets

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class WidgetJobSchedulingTest {
    private data class QueuedJob(
        val expedited: Boolean,
    )

    @Test
    fun `visible widget work uses expedited execution before requesting regular fallback`() {
        val attempts = mutableListOf<QueuedJob>()
        enqueueWidgetLifecycleWork(true, { null }, ::QueuedJob) {
            attempts.add(it)
            true
        }
        assertEquals(listOf(QueuedJob(true)), attempts)
    }

    @Test
    fun `exhausted expedited quota retains work through a regular fallback`() {
        val attempts = mutableListOf<QueuedJob>()
        enqueueWidgetLifecycleWork(true, { null }, ::QueuedJob) { job ->
            attempts.add(job)
            !job.expedited
        }
        assertEquals(listOf(QueuedJob(true), QueuedJob(false)), attempts)
    }

    @Test
    fun `new work reuses the exact existing job without interrupting its execution`() {
        val existing = QueuedJob(false)
        var enqueued: QueuedJob? = null
        enqueueWidgetLifecycleWork(true, { existing }, { error("An existing job must not be rebuilt") }) {
            enqueued = it
            true
        }
        assertSame(existing, enqueued)
    }

    @Test
    fun `another accepted job appearing during quota fallback keeps its metadata`() {
        val concurrent = QueuedJob(true)
        val attempts = mutableListOf<QueuedJob>()
        var reads = 0
        enqueueWidgetLifecycleWork(true, { if (reads++ == 0) null else concurrent }, ::QueuedJob) { job ->
            attempts.add(job)
            attempts.size > 1
        }
        assertSame(concurrent, attempts.last())
    }

    @Test
    fun `existing expedited job completing before enqueue still permits regular fallback`() {
        val existing = QueuedJob(true)
        val attempts = mutableListOf<QueuedJob>()
        var reads = 0
        enqueueWidgetLifecycleWork(true, { if (reads++ == 0) existing else null }, ::QueuedJob) { job ->
            attempts.add(job)
            !job.expedited
        }
        assertSame(existing, attempts.first())
        assertEquals(listOf(existing, QueuedJob(false)), attempts)
    }

    @Test
    fun `failed regular scheduling is not reported as acceptance or retried in a tight loop`() {
        var attempts = 0
        assertFailsWith<IllegalStateException> {
            enqueueWidgetLifecycleWork(false, { null }, ::QueuedJob) {
                attempts++
                false
            }
        }
        assertEquals(1, attempts)
        attempts = 0
        assertFailsWith<IllegalStateException> {
            enqueueWidgetLifecycleWork(true, { null }, ::QueuedJob) {
                attempts++
                false
            }
        }
        assertEquals(2, attempts)
    }
}
