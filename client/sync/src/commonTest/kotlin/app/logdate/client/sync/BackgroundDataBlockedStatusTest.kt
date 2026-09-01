package app.logdate.client.sync

import app.logdate.client.networking.DataRestriction
import app.logdate.client.sync.test.fakeDataUsagePolicy
import app.logdate.client.sync.test.fakeSessionStorage
import app.logdate.client.sync.test.testDefaultSyncManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Both of Willie's phones had LogDate on the platform's restrict-background list, so every
 * sync job the app enqueued was held by the OS and never ran. The app reported nothing at
 * all - the queue simply stopped draining, which is indistinguishable from "up to date".
 *
 * Retrying cannot fix a blocked job, so the only honest thing the app can do is say the
 * backup is paused and why.
 */
class BackgroundDataBlockedStatusTest {
    @Test
    fun `sync reports that it is paused when the platform blocks background network`() =
        runTest {
            val manager =
                testDefaultSyncManager(
                    sessionStorage = fakeSessionStorage(),
                    dataUsagePolicy = fakeDataUsagePolicy(restriction = DataRestriction.BACKGROUND_DATA_BLOCKED),
                )

            assertEquals(SyncPausedReason.BACKGROUND_DATA_OFF, manager.getSyncStatus().pausedReason)
        }

    @Test
    fun `sync reports that it is paused when the device has no connection`() =
        runTest {
            val manager =
                testDefaultSyncManager(
                    sessionStorage = fakeSessionStorage(),
                    dataUsagePolicy = fakeDataUsagePolicy(restriction = DataRestriction.OFFLINE),
                )

            assertEquals(SyncPausedReason.OFFLINE, manager.getSyncStatus().pausedReason)
        }

    @Test
    fun `sync reports no pause reason when the platform allows background network`() =
        runTest {
            val manager =
                testDefaultSyncManager(
                    sessionStorage = fakeSessionStorage(),
                    dataUsagePolicy = fakeDataUsagePolicy(),
                )

            assertNull(manager.getSyncStatus().pausedReason)
        }
}
