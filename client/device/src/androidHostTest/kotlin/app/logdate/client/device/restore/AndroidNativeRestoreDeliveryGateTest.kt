package app.logdate.client.device.restore

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidNativeRestoreDeliveryGateTest {
    @Test
    fun `overlapping completion waits until the previous completion releases its delivery boundary`() {
        AndroidNativeRestoreDeliveryGate.beginRestoreCompletion()
        val attempted = CountDownLatch(1)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val duplicate =
            thread {
                try {
                    attempted.countDown()
                    AndroidNativeRestoreDeliveryGate.beginRestoreCompletion()
                    entered.countDown()
                    try {
                        check(release.await(2, TimeUnit.SECONDS))
                    } finally {
                        AndroidNativeRestoreDeliveryGate.endRestore()
                    }
                } catch (error: Throwable) {
                    failure.set(error)
                }
            }
        try {
            assertTrue(attempted.await(1, TimeUnit.SECONDS))
            assertFalse(entered.await(100, TimeUnit.MILLISECONDS), "A duplicate completion cannot share the active completion owner")
        } finally {
            AndroidNativeRestoreDeliveryGate.endRestore()
            release.countDown()
            duplicate.join(1_000)
        }
        assertFalse(duplicate.isAlive)
        assertTrue(entered.count == 0L, "The queued completion must run after its predecessor")
        assertNull(failure.get())
    }

    @Test
    fun `failed overlapping callback cannot release reconciliation while another callback is active`() =
        runTest {
            AndroidNativeRestoreDeliveryGate.beginRestoreCallback()
            AndroidNativeRestoreDeliveryGate.beginRestoreCallback()
            var reconciled = false
            val reconciliation =
                backgroundScope.launch {
                    AndroidNativeRestoreDeliveryGate.withLock {
                        reconciled = true
                    }
                }
            runCurrent()

            assertFalse(reconciled)

            AndroidNativeRestoreDeliveryGate.endRestoreCallback()
            runCurrent()

            assertFalse(reconciled)

            AndroidNativeRestoreDeliveryGate.endRestoreCallback()
            AndroidNativeRestoreDeliveryGate.beginRestoreCompletion()
            AndroidNativeRestoreDeliveryGate.endRestore()
            runCurrent()

            assertTrue(reconciled)
            reconciliation.cancel()
        }

    @Test
    fun `completion waits for all active restore callbacks before recording delivery`() {
        AndroidNativeRestoreDeliveryGate.beginRestoreCallback()
        val completionStarted = CountDownLatch(1)
        val completionReturned = CountDownLatch(1)
        val completion =
            thread {
                completionStarted.countDown()
                AndroidNativeRestoreDeliveryGate.beginRestoreCompletion()
                completionReturned.countDown()
                AndroidNativeRestoreDeliveryGate.endRestore()
            }

        assertTrue(completionStarted.await(1, TimeUnit.SECONDS))
        assertFalse(completionReturned.await(50, TimeUnit.MILLISECONDS))

        AndroidNativeRestoreDeliveryGate.endRestoreCallback()

        assertTrue(completionReturned.await(1, TimeUnit.SECONDS))
        completion.join(1_000)
        assertFalse(completion.isAlive)
    }
}
