package app.logdate.wear.sync.v2.delivery

import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.ReceiptState
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.peer.delivery.DeliveryState
import app.logdate.client.sync.peer.delivery.EnvelopeDataMap
import app.logdate.client.sync.peer.delivery.HoldReason
import app.logdate.client.sync.peer.delivery.MomentEnvelopeBody
import app.logdate.client.sync.peer.delivery.PhoneReach
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchMomentDeliveryTest {
    private val f = MomentDeliveryFixture()

    @Test fun markUsesSeparateScopedEnvelopeAndWaitsForExactReceipt() =
        runTest {
            assertTrue(assertNotNull(f.delivery.deliverIfActive()).success)
            val sent = f.transport.puts.single()
            assertEquals(WearPaths.watchRecord(RecordKind.MOMENT, f.moment.id, f.peer, "mark-operation"), sent.first)
            val envelope = (EnvelopeDataMap.fromDataMap(sent.second) as Decoded.Supported).value
            assertEquals(f.moment, MomentEnvelopeBody.decode(envelope))
            assertEquals(f.peer, envelope.context)
            assertEquals(f.original.operationId, envelope.operationId)
            assertEquals(
                RecordKind.MOMENT,
                f.transport.requests
                    .single()
                    .items
                    .single()
                    .kind,
            )
            assertEquals(listOf(f.original), f.queue)
            assertEquals(DeliveryState.SENT, f.row()?.state)
            assertEquals(NoteSchedule.Ledger(31_000), f.delivery.schedule())
        }

    @Test fun unsupportedPhoneHoldsMarkAndResumesSchedulingWhenItNegotiates() =
        runTest {
            f.supported = false
            f.delivery.deliverIfActive()
            assertTrue(f.transport.puts.isEmpty())
            assertEquals(0, f.cloudCalls)
            assertEquals(listOf(f.original), f.queue)
            assertEquals(NoteSchedule.Ledger(null), f.delivery.schedule())
            f.supported = true
            assertEquals(NoteSchedule.Ledger(f.now), f.delivery.schedule())
            f.delivery.deliverIfActive()
            assertEquals(1, f.transport.puts.size)
        }

    @Test fun backedUpReceiptSettlesOnlyCapturedOperationAndCleansItsOwnItem() =
        runTest {
            f.response = f.proof(ReceiptState.BACKED_UP)
            assertEquals(1, f.delivery.deliverIfActive()?.uploadedItems)
            assertTrue(f.queue.isEmpty())
            assertTrue(f.transport.puts.isEmpty())
            assertEquals(listOf(WearPaths.watchRecord(RecordKind.MOMENT, f.moment.id, f.peer, "mark-operation")), f.transport.deleted)
        }

    @Test fun localPhoneReceiptSettlesPairOnlyButNotPendingCloudBackup() =
        runTest {
            f.response = f.proof(ReceiptState.ON_PHONE)
            f.delivery.deliverIfActive()
            assertEquals(listOf(f.original), f.queue)
            f.pairOnly()
            f.response = f.proof(ReceiptState.ON_PHONE)
            f.delivery.deliverIfActive()
            assertTrue(f.queue.isEmpty())
        }

    @Test fun foreignOperationAndRelinkedReceiptCannotClearWork() =
        runTest {
            f.response = f.proof(ReceiptState.BACKED_UP, operation = "old-operation")
            f.delivery.deliverIfActive()
            assertEquals(listOf(f.original), f.queue)
            f.response = f.proof(ReceiptState.BACKED_UP).copy(context = f.peer.copy(phoneNode = "other"))
            f.delivery.deliverIfActive()
            assertEquals(listOf(f.original), f.queue)
        }

    @Test fun cloudFallbackReleasesTypedCreateOnlyWithinTheActiveRun() =
        runTest {
            f.route = f.route.copy(phone = PhoneReach.UNREACHABLE)
            f.delivery.deliverIfActive()
            assertEquals(1, f.cloudCalls)
            assertTrue(f.cloudWasEligible)
            assertFalse(f.released.isEligible(app.logdate.client.sync.metadata.EntityType.MOMENT, f.original))
            assertEquals(listOf(f.original), f.queue)
            assertTrue(f.transport.puts.isEmpty())
        }

    @Test fun sensorEditGoesToNearbyPhoneButWaitsWhenOffline() =
        runTest {
            val edit = f.original.copy(operation = PendingOperation.UPDATE)
            f.queue = listOf(edit)
            f.route = f.route.copy(phone = PhoneReach.UNREACHABLE)
            f.delivery.deliverIfActive()
            assertEquals(0, f.cloudCalls)
            assertEquals(HoldReason.EDIT_NEEDS_PHONE, f.row(edit)?.holdReason)
            f.route = f.route.copy(phone = PhoneReach.NEARBY)
            assertEquals(NoteSchedule.Ledger(f.now), f.delivery.schedule())
            f.delivery.deliverIfActive()
            assertEquals(1, f.transport.puts.size)
            assertEquals(listOf(edit), f.queue)
        }

    @Test fun deletionNeedsExactRemovalReceiptAndNeverRequiresLiveBody() =
        runTest {
            val deletion = f.original.copy(operation = PendingOperation.DELETE)
            f.queue = listOf(deletion)
            f.stored = null
            f.response = f.proof(ReceiptState.DELETED_ON_PHONE)
            f.delivery.deliverIfActive()
            assertEquals(listOf(deletion), f.queue)
            val envelope =
                (
                    EnvelopeDataMap.fromDataMap(
                        f.transport.puts
                            .single()
                            .second,
                    ) as Decoded.Supported
                ).value
            assertTrue(envelope.removed)
            assertNull(envelope.body)
            f.response = f.proof(ReceiptState.DELETED_ON_PHONE, removed = true)
            f.delivery.deliverIfActive()
            assertTrue(f.queue.isEmpty())
        }

    @Test fun inFlightEditSurvivesEarlierReceipt() =
        runTest {
            f.response = f.proof(ReceiptState.BACKED_UP)
            val newer = f.original.copy(operation = PendingOperation.UPDATE, operationId = "new-operation")
            f.transport.beforeAnswer = { f.queue = listOf(newer) }
            f.delivery.deliverIfActive()
            assertEquals(listOf(newer), f.queue)
            assertTrue(f.transport.puts.isEmpty())
        }

    @Test fun transportFailureBacksOffAndCancellationRetainsWork() =
        runTest {
            f.transport.succeeds = false
            assertFalse(assertNotNull(f.delivery.deliverIfActive()).success)
            assertEquals(31_000L, f.row()?.nextAttemptAt)
            f.delivery.deliverIfActive()
            assertEquals(1, f.transport.puts.size)
            f.now = 31_000
            f.transport.beforePut = { throw CancellationException("stopped") }
            assertFailsWith<CancellationException> { f.delivery.deliverIfActive() }
            assertEquals(listOf(f.original), f.queue)
        }

    @Test fun flagOffAndMissingBodyNeverSendOrSettle() =
        runTest {
            f.active = false
            assertNull(f.delivery.deliverIfActive())
            f.active = true
            f.stored = null
            f.delivery.deliverIfActive()
            assertTrue(f.transport.puts.isEmpty())
            assertEquals(listOf(f.original), f.queue)
        }
}
