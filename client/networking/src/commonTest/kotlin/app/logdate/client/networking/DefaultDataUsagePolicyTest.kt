package app.logdate.client.networking

import app.logdate.client.networking.saver.ConfigurableNetworkSaverModeProvider
import app.logdate.client.networking.saver.NetworkConnectionType
import app.logdate.client.networking.saver.NetworkSaverState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Validates the [DefaultDataUsagePolicy] logic for determining network usage restrictions.
 *
 * These tests ensure that the application correctly transitions between unrestricted,
 * conservative, and restricted data modes based on the combination of network type
 * (Wi-Fi, Cellular, etc.) and the system's data saver state. It also verifies the
 * behavior of extension functions that control specific features like media sync and
 * AI calls.
 */
class DefaultDataUsagePolicyTest {
    private val networkSaverProvider = ConfigurableNetworkSaverModeProvider()
    private val policy = DefaultDataUsagePolicy(networkSaverProvider)

    @Test
    fun noConnection_returnsRestricted() =
        runTest {
            networkSaverProvider.setNetworkSaverState(
                NetworkSaverState(isDataSaverEnabled = false, connectionType = NetworkConnectionType.NONE),
            )
            assertIs<DataUsageMode.Restricted>(policy.currentMode())
        }

    @Test
    fun cellularWithDataSaverOn_returnsRestricted() =
        runTest {
            networkSaverProvider.setNetworkSaverState(
                NetworkSaverState(isDataSaverEnabled = true, connectionType = NetworkConnectionType.CELLULAR),
            )
            assertIs<DataUsageMode.Restricted>(policy.currentMode())
        }

    @Test
    fun cellularWithDataSaverOff_returnsConservative() =
        runTest {
            networkSaverProvider.setNetworkSaverState(
                NetworkSaverState(isDataSaverEnabled = false, connectionType = NetworkConnectionType.CELLULAR),
            )
            assertIs<DataUsageMode.Conservative>(policy.currentMode())
        }

    @Test
    fun wifiWithDataSaverOff_returnsUnrestricted() =
        runTest {
            networkSaverProvider.setNetworkSaverState(
                NetworkSaverState(isDataSaverEnabled = false, connectionType = NetworkConnectionType.WIFI),
            )
            assertIs<DataUsageMode.Unrestricted>(policy.currentMode())
        }

    @Test
    fun wifiWithDataSaverOn_returnsRestricted() =
        runTest {
            networkSaverProvider.setNetworkSaverState(
                NetworkSaverState(isDataSaverEnabled = true, connectionType = NetworkConnectionType.WIFI),
            )
            assertIs<DataUsageMode.Restricted>(policy.currentMode())
        }

    @Test
    fun ethernetWithDataSaverOff_returnsUnrestricted() =
        runTest {
            networkSaverProvider.setNetworkSaverState(
                NetworkSaverState(isDataSaverEnabled = false, connectionType = NetworkConnectionType.ETHERNET),
            )
            assertIs<DataUsageMode.Unrestricted>(policy.currentMode())
        }

    @Test
    fun ethernetWithDataSaverOn_returnsRestricted() =
        runTest {
            networkSaverProvider.setNetworkSaverState(
                NetworkSaverState(isDataSaverEnabled = true, connectionType = NetworkConnectionType.ETHERNET),
            )
            assertIs<DataUsageMode.Restricted>(policy.currentMode())
        }

    @Test
    fun otherConnectionWithDataSaverOff_returnsUnrestricted() =
        runTest {
            networkSaverProvider.setNetworkSaverState(
                NetworkSaverState(isDataSaverEnabled = false, connectionType = NetworkConnectionType.OTHER),
            )
            assertIs<DataUsageMode.Unrestricted>(policy.currentMode())
        }

    @Test
    fun policyFlowEmitsOnStateChange() =
        runTest {
            networkSaverProvider.setNetworkSaverState(
                NetworkSaverState(isDataSaverEnabled = false, connectionType = NetworkConnectionType.WIFI),
            )
            assertIs<DataUsageMode.Unrestricted>(policy.policy.first())

            networkSaverProvider.setNetworkSaverState(
                NetworkSaverState(isDataSaverEnabled = false, connectionType = NetworkConnectionType.CELLULAR),
            )
            assertIs<DataUsageMode.Conservative>(policy.policy.first())

            networkSaverProvider.setNetworkSaverState(
                NetworkSaverState(isDataSaverEnabled = true, connectionType = NetworkConnectionType.CELLULAR),
            )
            assertIs<DataUsageMode.Restricted>(policy.policy.first())
        }

    // Extension function tests

    @Test
    fun shouldSyncMedia_onlyUnrestricted() {
        assertTrue(DataUsageMode.Unrestricted.shouldSyncMedia())
        assertFalse(DataUsageMode.Conservative.shouldSyncMedia())
        assertFalse(DataUsageMode.Restricted.shouldSyncMedia())
    }

    @Test
    fun shouldSyncMetadata_unrestrictedAndConservative() {
        assertTrue(DataUsageMode.Unrestricted.shouldSyncMetadata())
        assertTrue(DataUsageMode.Conservative.shouldSyncMetadata())
        assertFalse(DataUsageMode.Restricted.shouldSyncMetadata())
    }

    @Test
    fun shouldLoadFullResImages_onlyUnrestricted() {
        assertTrue(DataUsageMode.Unrestricted.shouldLoadFullResImages())
        assertFalse(DataUsageMode.Conservative.shouldLoadFullResImages())
        assertFalse(DataUsageMode.Restricted.shouldLoadFullResImages())
    }

    @Test
    fun shouldLoadReducedImages_onlyConservative() {
        assertFalse(DataUsageMode.Unrestricted.shouldLoadReducedImages())
        assertTrue(DataUsageMode.Conservative.shouldLoadReducedImages())
        assertFalse(DataUsageMode.Restricted.shouldLoadReducedImages())
    }

    @Test
    fun shouldPrefetchImages_onlyUnrestricted() {
        assertTrue(DataUsageMode.Unrestricted.shouldPrefetchImages())
        assertFalse(DataUsageMode.Conservative.shouldPrefetchImages())
        assertFalse(DataUsageMode.Restricted.shouldPrefetchImages())
    }

    @Test
    fun shouldAllowAICalls_unrestrictedAndConservative() {
        assertTrue(DataUsageMode.Unrestricted.shouldAllowAICalls())
        assertTrue(DataUsageMode.Conservative.shouldAllowAICalls())
        assertFalse(DataUsageMode.Restricted.shouldAllowAICalls())
    }
}
