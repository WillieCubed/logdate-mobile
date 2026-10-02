package app.logdate.client.sync.diagnostics

import app.logdate.client.sync.metadata.UploadScope
import app.logdate.shared.model.diagnostics.DiagnosticOutcome
import app.logdate.shared.model.diagnostics.DiagnosticPhase
import app.logdate.shared.model.diagnostics.SyncDiagnosticEvent
import app.logdate.shared.model.diagnostics.SyncDiagnosticReport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagnosticReportingTest {
    private class Storage : DiagnosticStorage {
        var value: String? = null

        override suspend fun read() = value

        override suspend fun write(value: String) {
            this.value = value
        }

        override suspend fun clear() {
            value = null
        }
    }

    private val owner = UploadScope("owner-a", "https://private-a.example")
    private val event = SyncDiagnosticEvent(DiagnosticPhase.MEDIA, DiagnosticOutcome.FAILED)

    @Test
    fun `unscoped and delayed other-account events never enter automatic reporting`() =
        runTest {
            var sends = 0
            val reporter =
                DiagnosticReporting(Storage(), { owner }, { true }, { 1000L }) { _, _ ->
                    sends++
                    DiagnosticDelivery.ACCEPTED
                }
            reporter.enable(owner)
            reporter.capture(event, null)
            reporter.capture(event, UploadScope("previous-owner", owner.serverOrigin))
            reporter.deliverOnce()
            assertEquals(0, sends)
        }

    @Test
    fun `off sends nothing and enabling never captures prior events`() =
        runTest {
            val sent = mutableListOf<SyncDiagnosticReport>()
            val reporter =
                DiagnosticReporting(Storage(), { owner }, { true }, { 1000L }) { _, report ->
                    sent += report
                    DiagnosticDelivery.ACCEPTED
                }
            reporter.capture(event, owner)
            reporter.deliverOnce()
            assertTrue(sent.isEmpty())
            reporter.enable(owner)
            reporter.deliverOnce()
            assertTrue(sent.isEmpty())
            reporter.capture(event, owner)
            reporter.deliverOnce()
            assertEquals(1, sent.size)
            assertEquals(1, sent.single().events.size)
        }

    @Test
    fun `restart and lost response retry identical report`() =
        runTest {
            val storage = Storage()
            val sent = mutableListOf<SyncDiagnosticReport>()
            var clock = 0L

            fun reporter() =
                DiagnosticReporting(storage, { owner }, { true }, { clock }) { _, report ->
                    sent += report
                    if (sent.size == 1) DiagnosticDelivery.RETRY else DiagnosticDelivery.ACCEPTED
                }
            reporter().apply {
                enable(owner)
                capture(event, owner)
                deliverOnce()
            }
            clock = 6 * 60 * 60 * 1000L
            reporter().deliverOnce()
            assertEquals(2, sent.size)
            assertEquals(sent.first(), sent.last())
        }

    @Test
    fun `switch clears unsent reports and returning requires fresh consent`() =
        runTest {
            var selected = owner
            var sends = 0
            val reporter =
                DiagnosticReporting(Storage(), { selected }, { true }, { 1000L }) { _, _ ->
                    sends++
                    DiagnosticDelivery.ACCEPTED
                }
            reporter.enable(owner)
            reporter.capture(event, owner)
            selected = UploadScope("owner-b", owner.serverOrigin)
            reporter.refreshScope()
            selected = owner
            reporter.capture(event, owner)
            reporter.deliverOnce()
            assertEquals(0, sends)
            assertFalse(reporter.status().enabled)
            assertEquals(0, reporter.status().pendingCount)
        }

    @Test
    fun `revoke cancels active send and removes unsent reports durably`() =
        runTest {
            val entered = CompletableDeferred<Unit>()
            val blocked = CompletableDeferred<Unit>()
            val storage = Storage()
            val reporter =
                DiagnosticReporting(storage, { owner }, { true }, { 1000L }) { _, _ ->
                    entered.complete(Unit)
                    blocked.await()
                    DiagnosticDelivery.ACCEPTED
                }
            reporter.enable(owner)
            reporter.capture(event, owner)
            val delivery = async { reporter.deliverOnce() }
            withTimeout(1000) { entered.await() }
            reporter.disable()
            runCurrent()
            assertTrue(delivery.isCompleted)
            assertEquals(0, reporter.status().pendingCount)
            assertFalse(
                DiagnosticReporting(storage, { owner }, { true }, { 1000L }) { _, _ ->
                    error("Revoked report transmitted")
                }.status().enabled,
            )
        }

    @Test
    fun `unsupported destination cannot accept consent`() =
        runTest {
            val reporter =
                DiagnosticReporting(Storage(), { owner }, { false }, { 1000L }) { _, _ ->
                    error("Unsupported report transmitted")
                }
            assertTrue(runCatching { reporter.enable(owner) }.isFailure)
            reporter.capture(event, owner)
            reporter.deliverOnce()
            assertFalse(reporter.status().enabled)
        }

    @Test
    fun `revocation during suspended storage cannot leave future delivery stuck`() =
        runTest {
            val storageEntered = CompletableDeferred<Unit>()
            val releaseStorage = CompletableDeferred<Unit>()
            var blockWrite = false
            val storage =
                object : DiagnosticStorage {
                    var value: String? = null

                    override suspend fun read() = value

                    override suspend fun clear() {
                        value = null
                    }

                    override suspend fun write(value: String) {
                        if (blockWrite) {
                            blockWrite = false
                            storageEntered.complete(Unit)
                            releaseStorage.await()
                        }
                        this.value = value
                    }
                }
            val sendEntered = CompletableDeferred<Unit>()
            var sends = 0
            val reporter =
                DiagnosticReporting(storage, { owner }, { true }, { 1000L }) { _, _ ->
                    sends++
                    if (sends == 1) {
                        sendEntered.complete(Unit)
                        kotlinx.coroutines.awaitCancellation()
                    }
                    DiagnosticDelivery.ACCEPTED
                }
            reporter.enable(owner)
            reporter.capture(event, owner)
            val delivery = async { reporter.deliverOnce() }
            sendEntered.await()
            blockWrite = true
            val revoke = async { reporter.disable() }
            storageEntered.await()
            delivery.cancel()
            runCurrent()
            releaseStorage.complete(Unit)
            revoke.await()
            runCatching { delivery.await() }
            reporter.enable(owner)
            reporter.capture(event, owner)
            reporter.deliverOnce()
            assertEquals(2, sends)
        }

    @Test
    fun `revocation does not suppress cancellation of the calling job`() =
        runTest {
            val entered = CompletableDeferred<Unit>()
            val reporter =
                DiagnosticReporting(Storage(), { owner }, { true }, { 1000L }) { _, _ ->
                    entered.complete(Unit)
                    kotlinx.coroutines.awaitCancellation()
                }
            reporter.enable(owner)
            reporter.capture(event, owner)
            val parent = kotlinx.coroutines.Job()
            val caller = kotlinx.coroutines.CoroutineScope(backgroundScope.coroutineContext + parent)
            var continuedAfterCancellation = false
            val delivery =
                caller.async {
                    reporter.deliverOnce()
                    continuedAfterCancellation = true
                }
            entered.await()
            reporter.disable()
            parent.cancel()
            runCurrent()
            assertTrue(delivery.isCancelled)
            assertFalse(continuedAfterCancellation)
        }

    @Test
    fun `changed durable epoch revokes consent even after returning to the same scope and restarting`() =
        runTest {
            val storage = Storage()
            var epoch = "00000000-0000-4000-8000-000000000001"
            var sends = 0

            fun reporter() =
                DiagnosticReporting(storage, { owner }, { true }, { 1000L }, { epoch }) { _, _ ->
                    sends++
                    DiagnosticDelivery.ACCEPTED
                }
            reporter().apply {
                enable(owner)
                capture(event, owner)
            }
            epoch = "00000000-0000-4000-8000-000000000002"
            val restarted = reporter()
            restarted.deliverOnce()
            assertEquals(0, sends)
            assertFalse(restarted.status().enabled)
            restarted.enable(owner)
            restarted.capture(event, owner)
            restarted.deliverOnce()
            assertEquals(1, sends)
        }

    @Test
    fun `queued events retain original consent admission and cannot enter a later grant`() =
        runTest {
            var sends = 0
            val reporter =
                DiagnosticReporting(Storage(), { owner }, { true }, { 1000L }) { _, _ ->
                    sends++
                    DiagnosticDelivery.ACCEPTED
                }
            val beforeOptIn = reporter.admission(owner)
            reporter.enable(owner)
            val firstGrant = reporter.admission(owner)
            reporter.disable()
            reporter.enable(owner)
            reporter.captureAdmitted(event, owner, beforeOptIn)
            reporter.captureAdmitted(event, owner, firstGrant)
            reporter.deliverOnce()
            assertEquals(0, sends)
            reporter.captureAdmitted(event, owner, reporter.admission(owner))
            reporter.deliverOnce()
            assertEquals(1, sends)
        }

    @Test
    fun `temporary loss of server capability does not hide active consent from revocation controls`() =
        runTest {
            var available = true
            val reporter = DiagnosticReporting(Storage(), { owner }, { available }, { 1000L }) { _, _ -> DiagnosticDelivery.ACCEPTED }
            reporter.enable(owner)
            available = false
            assertTrue(reporter.status().enabled)
            reporter.disable()
            available = true
            assertFalse(reporter.status().enabled)
        }
}
