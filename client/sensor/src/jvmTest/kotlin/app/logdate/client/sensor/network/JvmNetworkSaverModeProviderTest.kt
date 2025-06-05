package app.logdate.client.sensor.network

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class JvmNetworkSaverModeProviderTest {

    private lateinit var networkSaverModeProvider: JvmNetworkSaverModeProvider
    
    @BeforeTest
    fun setup() {
        networkSaverModeProvider = JvmNetworkSaverModeProvider()
    }
    
    @Test
    fun testInitialNetworkState() = runTest {
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
    fun testNetworkStateFlow() = runTest {
        val state = networkSaverModeProvider.dataSaverModeState.first()
        assertNotNull(state)
        assertFalse(state.isDataSaverEnabled)
    }
    
    @Test
    fun testDataSaverMode() = runTest {
        val isDataSaverMode = networkSaverModeProvider.isDataSaverModeActive()
        assertFalse(isDataSaverMode)
    }
    
    @Test
    fun testCleanup() {
        // Just verify that cleanup doesn't throw any exceptions
        networkSaverModeProvider.cleanup()
    }
}