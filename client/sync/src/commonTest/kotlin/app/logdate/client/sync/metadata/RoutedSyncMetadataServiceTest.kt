package app.logdate.client.sync.metadata

import app.logdate.client.sync.test.fakeSyncMetadataService
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Tests [RoutedSyncMetadataService]: an upload the router keeps away from the cloud is hidden from the
 * upload pass only, and still counts everywhere else.
 */
class RoutedSyncMetadataServiceTest {
    private val delegate = fakeSyncMetadataService()
    private val routed = RoutedSyncMetadataService(delegate) { _, pending -> pending.operation != PendingOperation.UPDATE }

    @Test
    fun `uploads that are not eligible are hidden from the upload pass`() =
        runTest {
            delegate.enqueuePending("created", EntityType.NOTE, PendingOperation.CREATE)
            delegate.enqueuePending("edited", EntityType.NOTE, PendingOperation.UPDATE)

            assertEquals(listOf("created"), routed.getPendingUploads(EntityType.NOTE).map { it.entityId })
        }

    @Test
    fun `a hidden upload still counts as pending for downloads and the status`() =
        runTest {
            delegate.enqueuePending("edited", EntityType.NOTE, PendingOperation.UPDATE)

            assertTrue(routed.hasPending(EntityType.NOTE, "edited"))
            assertEquals(1, routed.getPendingCount())
        }

    @Test
    fun `settling goes to the real outbox`() =
        runTest {
            delegate.enqueuePending("created", EntityType.NOTE, PendingOperation.CREATE)
            val pending = routed.getPendingUploads(EntityType.NOTE).single()

            assertTrue(routed.settleIfCurrent(EntityType.NOTE, pending, Instant.fromEpochMilliseconds(1), 1))
            assertTrue(delegate.getPendingUploads(EntityType.NOTE).isEmpty())
        }
}
