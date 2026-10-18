package app.logdate.client.device.restore

import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NativeRestoreSessionBoundaryTest {
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
