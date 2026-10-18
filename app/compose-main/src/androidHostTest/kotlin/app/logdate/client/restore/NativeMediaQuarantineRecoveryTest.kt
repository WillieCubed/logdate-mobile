package app.logdate.client.restore

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NativeMediaQuarantineRecoveryTest {
    @Test
    fun `startup reconciliation cannot finish before restored references are checked`() =
        runTest {
            val releaseReconciliation = CompletableDeferred<Boolean>()
            var acknowledged = false
            val recovery =
                NativeMediaQuarantineRecovery(
                    pendingDelivery = { "delivery-1" },
                    quarantine = { releaseReconciliation.await() },
                    acknowledge = {
                        acknowledged = true
                        true
                    },
                    enqueue = { error("Complete reconciliation should not enqueue work") },
                )
            val startup = async { recovery.run("delivery-1") }
            runCurrent()

            assertFalse(startup.isCompleted)
            assertFalse(acknowledged)

            releaseReconciliation.complete(true)

            assertEquals(NativeMediaQuarantineResult.COMPLETE, startup.await())
            assertTrue(acknowledged)
        }

    @Test
    fun `incomplete availability checks keep the durable delivery pending`() =
        runTest {
            var acknowledged = false
            var enqueued: String? = null
            val recovery =
                NativeMediaQuarantineRecovery(
                    pendingDelivery = { "delivery-1" },
                    quarantine = { false },
                    acknowledge = {
                        acknowledged = true
                        true
                    },
                    enqueue = { enqueued = it },
                )

            assertEquals(NativeMediaQuarantineResult.RETRY, recovery.run("delivery-1"))
            assertFalse(acknowledged)
            assertEquals(null, enqueued)
        }

    @Test
    fun `a newer delivery is scheduled without acknowledging it from stale work`() =
        runTest {
            var acknowledged = false
            var enqueued: String? = null
            val recovery =
                NativeMediaQuarantineRecovery(
                    pendingDelivery = { "delivery-2" },
                    quarantine = { error("Stale worker must not reconcile new delivery") },
                    acknowledge = {
                        acknowledged = true
                        true
                    },
                    enqueue = { enqueued = it },
                )

            assertEquals(NativeMediaQuarantineResult.HANDED_OFF, recovery.run("delivery-1"))
            assertFalse(acknowledged)
            assertEquals("delivery-2", enqueued)
        }

    @Test
    fun `a failed handoff keeps the old worker retryable until the new delivery is queued`() =
        runTest {
            val recovery =
                NativeMediaQuarantineRecovery(
                    pendingDelivery = { "delivery-2" },
                    quarantine = { error("Stale worker must not reconcile new delivery") },
                    acknowledge = { error("A failed handoff cannot acknowledge either delivery") },
                    enqueue = { throw IllegalStateException("WorkManager persistence failed") },
                )

            assertEquals(NativeMediaQuarantineResult.RETRY, recovery.run("delivery-1"))
        }

    @Test
    fun `completed checks acknowledge only the delivery they reconciled`() =
        runTest {
            var acknowledged: String? = null
            val recovery =
                NativeMediaQuarantineRecovery(
                    pendingDelivery = { "delivery-1" },
                    quarantine = { true },
                    acknowledge = {
                        acknowledged = it
                        true
                    },
                    enqueue = { error("No retry should be scheduled") },
                )

            assertEquals(NativeMediaQuarantineResult.COMPLETE, recovery.run("delivery-1"))
            assertEquals("delivery-1", acknowledged)
        }

    @Test
    fun `transient storage failures request durable retry`() =
        runTest {
            val recovery =
                NativeMediaQuarantineRecovery(
                    pendingDelivery = { "delivery-1" },
                    quarantine = { throw IllegalStateException("temporary database failure") },
                    acknowledge = { error("Failure must keep delivery pending") },
                    enqueue = { error("WorkManager retries this delivery") },
                )

            assertEquals(NativeMediaQuarantineResult.RETRY, recovery.run("delivery-1"))
        }

    @Test
    fun `foreground startup reconciles a newer delivery before it can finish`() =
        runTest {
            var pending: String? = "delivery-1"
            val reconciled = mutableListOf<String>()
            val acknowledged = mutableListOf<String>()
            val enqueued = mutableListOf<String>()

            awaitNativeMediaQuarantine(
                pendingDelivery = { pending },
                quarantine = { _ ->
                    val delivery = requireNotNull(pending)
                    reconciled += delivery
                    if (delivery == "delivery-1") pending = "delivery-2"
                    true
                },
                acknowledge = { delivery ->
                    if (delivery != pending) {
                        false
                    } else {
                        acknowledged += delivery
                        pending = null
                        true
                    }
                },
                enqueue = { enqueued += it },
            )

            assertEquals(listOf("delivery-1", "delivery-2"), reconciled)
            assertEquals(listOf("delivery-2"), acknowledged)
            assertEquals(listOf("delivery-2"), enqueued)
        }

    @Test
    fun `a transient marker read failure remains retryable`() =
        runTest {
            val recovery =
                NativeMediaQuarantineRecovery(
                    pendingDelivery = { throw IllegalStateException("temporary marker read failure") },
                    quarantine = { error("Marker must be read before reconciliation") },
                    acknowledge = { error("Failed marker reads cannot acknowledge delivery") },
                    enqueue = { error("WorkManager retries this delivery") },
                )

            assertEquals(NativeMediaQuarantineResult.RETRY, recovery.run("delivery-1"))
        }

    @Test
    fun `cancellation propagates so WorkManager can retain its retry state`() =
        runTest {
            val recovery =
                NativeMediaQuarantineRecovery(
                    pendingDelivery = { "delivery-1" },
                    quarantine = { throw CancellationException("worker stopped") },
                    acknowledge = { error("Cancelled work cannot acknowledge delivery") },
                    enqueue = { error("Cancellation is managed by WorkManager") },
                )

            var cancelled = false
            try {
                recovery.run("delivery-1")
            } catch (_: CancellationException) {
                cancelled = true
            }
            assertTrue(cancelled)
        }
}
