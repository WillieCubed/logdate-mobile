package app.logdate.wear.sync.v2.delivery

import app.logdate.client.sync.SyncError
import app.logdate.client.sync.SyncErrorType
import app.logdate.client.sync.SyncResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WatchCaptureDeliveryTest {
    private val notes = mockk<WatchNoteDelivery>()
    private val moments = mockk<WatchMomentDelivery>()
    private val combined = WatchCaptureDelivery(notes, moments)

    @Test fun combinedDeliveryRetainsBothCountsAndMomentFailures() =
        runTest {
            val error = SyncError(SyncErrorType.NETWORK_ERROR, "Marked moment waiting", retryable = true)
            coEvery { notes.deliverIfActive() } returns SyncResult(success = true, uploadedItems = 2)
            coEvery { moments.deliverIfActive() } returns SyncResult(success = false, uploadedItems = 1, errors = listOf(error))
            val result = combined.deliverIfActive()!!
            assertEquals(false, result.success)
            assertEquals(3, result.uploadedItems)
            assertEquals(listOf(error), result.errors)
        }

    @Test fun unsupportedOperationProtocolPreservesLegacyFallback() =
        runTest {
            coEvery { notes.deliverIfActive() } returns null
            coEvery { moments.deliverIfActive() } throws AssertionError("Legacy fallback was intercepted")
            assertNull(combined.deliverIfActive())
        }

    @Test fun earliestTypedDeadlineWinsWithoutChangingFlagOffScheduling() =
        runTest {
            coEvery { notes.schedule() } returns NoteSchedule.Ledger(5000)
            coEvery { moments.schedule() } returns NoteSchedule.Ledger(1000)
            assertEquals(NoteSchedule.Ledger(1000), combined.schedule())
            coEvery { notes.schedule() } returns NoteSchedule.Outbox
            assertEquals(NoteSchedule.Outbox, combined.schedule())
        }
}
