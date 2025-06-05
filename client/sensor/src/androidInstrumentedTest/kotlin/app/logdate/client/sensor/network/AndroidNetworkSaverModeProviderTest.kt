package app.logdate.client.sensor.network

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidNetworkSaverModeProviderTest {
    
    private lateinit var context: Context
    private lateinit var networkSaverModeProvider: AndroidNetworkSaverModeProvider
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        networkSaverModeProvider = AndroidNetworkSaverModeProvider(context)
    }
    
    @Test
    fun testNetworkStateNotNull() = runTest {
        val networkState = networkSaverModeProvider.getCurrentDataSaverState()
        assertNotNull(networkState)
        assertNotNull(networkState.connectionType)
    }
    
    @Test
    fun testNetworkStateFlow() = runTest {
        val networkState = networkSaverModeProvider.dataSaverModeState.first()
        assertNotNull(networkState)
    }
    
    @Test
    fun testDataSaverModeIsBoolean() = runTest {
        val isDataSaverMode = networkSaverModeProvider.isDataSaverModeActive()
        // Just verify it returns a boolean value without error
        assertNotNull(isDataSaverMode)
    }
    
    @Test
    fun testConnectionTypeValid() = runTest {
        val networkState = networkSaverModeProvider.getCurrentDataSaverState()
        val validTypes = NetworkConnectionType.values().toList()
        // Verify we get a valid connection type
        assert(networkState.connectionType in validTypes)
    }
}