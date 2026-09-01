package app.logdate.client.sensor.network

import app.logdate.client.networking.saver.NetworkConnectionType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * Tests the JVM (desktop/stub) implementation of [JvmNetworkSaverModeProvider].
 *
 * This suite ensures that the provider returns predictable network and data saver
 * states on non-Android platforms, providing a baseline for cross-platform sensor code.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JvmNetworkSaverModeProviderTest {
    private lateinit var networkSaverModeProvider: JvmNetworkSaverModeProvider

    @BeforeTest
    fun setup() {
        networkSaverModeProvider = JvmNetworkSaverModeProvider()
    }

    @Test
    fun `initial network state`() =
        runTest {
            val state = networkSaverModeProvider.getCurrentDataSaverState()
            assertNotNull(state)

            // Data saver mode should be false on JVM
            assertFalse(state.isDataSaverEnabled)

            // Connection type should be a valid enum value
            assertNotNull(state.connectionType)

            // Verify it's a valid connection type
            val validTypes = NetworkConnectionType.values().toList()
            assert(state.connectionType in validTypes)
        }

    @Test
    fun `network state flow`() =
        runTest {
            val state = networkSaverModeProvider.dataSaverModeState.first()
            assertNotNull(state)
            assertFalse(state.isDataSaverEnabled)
        }

    @Test
    fun `data saver mode`() =
        runTest {
            val isDataSaverMode = networkSaverModeProvider.isDataSaverModeActive()
            assertFalse(isDataSaverMode)
        }

    @Test
    fun `cleanup`() {
        // Just verify that cleanup doesn't throw any exceptions
        networkSaverModeProvider.cleanup()
    }
}
