package app.logdate.client.device.restore

import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NativeRestoreSessionBoundaryTest {
    @Test
    fun `recreated agent resumes a persisted final decision without replacing its delivery generation`() {
        val directory = createTempDirectory("native-completion-recreation").toFile()
        try {
            val journal = NativePreferenceDeliveryJournal(File(directory, "delivery.json"), "delivery-one")
            val names = setOf("logdate_widget_instances")
            journal.record(names, names)
            val starts = AtomicInteger()
            val recreated = NativeRestoreSessionBoundary { starts.incrementAndGet() }

            recreated.beginIfNeeded { journal.read() != null }
            recreated.beginIfNeeded()

            assertEquals(0, starts.get(), "A final delivery decision must keep its original generation")
            assertTrue(recreated.isStarted())
            assertEquals(setOf("logdate_widget_instances"), journal.record(names, emptySet()).delivered)
            assertNotNull(journal.read())
            recreated.finish()
            recreated.beginIfNeeded { false }
            assertEquals(1, starts.get(), "A subsequent delivery without a resumable decision starts a new generation")
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `failed resume inspection preserves retry without opening a replacement generation`() {
        val starts = AtomicInteger()
        val boundary = NativeRestoreSessionBoundary { starts.incrementAndGet() }

        assertFailsWith<IOException> { boundary.beginIfNeeded { throw IOException("Synthetic read failure") } }
        assertFalse(boundary.isStarted())
        assertEquals(0, starts.get())

        boundary.beginIfNeeded { true }
        assertTrue(boundary.isStarted())
        assertEquals(0, starts.get())
    }

    @Test
    fun `overlapping file callbacks begin one delivery generation`() {
        val callbacks = 8
        val starts = AtomicInteger()
        val boundary = NativeRestoreSessionBoundary { starts.incrementAndGet() }
        val ready = CountDownLatch(callbacks)
        val release = CountDownLatch(1)
        val workers =
            List(callbacks) {
                thread {
                    ready.countDown()
                    check(release.await(1, TimeUnit.SECONDS))
                    boundary.beginIfNeeded()
                }
            }

        assertTrue(ready.await(1, TimeUnit.SECONDS))
        release.countDown()
        workers.forEach { it.join(1_000) }

        assertTrue(workers.none { it.isAlive })
        assertEquals(1, starts.get())
        boundary.finish()
        boundary.beginIfNeeded()
        assertEquals(2, starts.get())
    }
}
