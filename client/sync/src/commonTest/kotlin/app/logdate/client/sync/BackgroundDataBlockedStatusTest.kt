package app.logdate.client.sync

import app.logdate.client.networking.DataRestriction
import app.logdate.client.networking.DefaultDataUsagePolicy
import app.logdate.client.networking.saver.ConfigurableNetworkSaverModeProvider
import app.logdate.client.networking.saver.NetworkConnectionType
import app.logdate.client.networking.saver.NetworkSaverState
import app.logdate.client.sync.test.fakeDataUsagePolicy
import app.logdate.client.sync.test.fakeSessionStorage
import app.logdate.client.sync.test.testDefaultSyncManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Covers live connection changes and the separate background-work restriction indicator. */
@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundDataBlockedStatusTest {
    @Test
    fun `published pause clears when a connection returns without another sync run`() =
        runTest {
            val scope = TestScope(StandardTestDispatcher(testScheduler))
            val provider = ConfigurableNetworkSaverModeProvider()
            provider.setNetworkSaverState(NetworkSaverState(false, NetworkConnectionType.NONE))
            val manager =
                testDefaultSyncManager(
                    sessionStorage = fakeSessionStorage(),
                    dataUsagePolicy = DefaultDataUsagePolicy(provider),
                    syncScope = scope,
                )
            scope.advanceUntilIdle()
            assertEquals(SyncPausedReason.OFFLINE, manager.syncStatusFlow.value.pausedReason)

            provider.setNetworkSaverState(NetworkSaverState(false, NetworkConnectionType.WIFI))
            scope.advanceUntilIdle()
            assertNull(manager.syncStatusFlow.value.pausedReason)
        }

    @Test
    fun `background restriction does not claim manual backup is paused`() =
        runTest {
            val manager =
                testDefaultSyncManager(
                    sessionStorage = fakeSessionStorage(),
                    dataUsagePolicy = fakeDataUsagePolicy(restriction = DataRestriction.BACKGROUND_DATA_BLOCKED),
                )

            val status = manager.getSyncStatus()
            assertNull(status.pausedReason)
            kotlin.test.assertTrue(status.backgroundWorkLimited)
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
