package app.logdate.client.device.restore

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidNativeRestoreDeliveryGateTest {
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
