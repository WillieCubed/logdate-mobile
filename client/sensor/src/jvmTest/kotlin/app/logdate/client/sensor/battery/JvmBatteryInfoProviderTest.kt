package app.logdate.client.sensor.battery

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class JvmBatteryInfoProviderTest {

    private lateinit var batteryInfoProvider: JvmBatteryInfoProvider
    
    @BeforeTest
    fun setup() {
        batteryInfoProvider = JvmBatteryInfoProvider()
    }
    
    @Test
    fun testInitialBatteryState() = runTest {
        val state = batteryInfoProvider.getCurrentBatteryState()
        assertNotNull(state)
        
        // JVM implementation should provide these default values
        assertEquals(100, state.level)
        assertEquals(true, state.isCharging)
        assertEquals(false, state.isPowerSaveMode)
    }
    
    @Test
    fun testBatteryFlow() = runTest {
        val state = batteryInfoProvider.currentBatteryState.first()
        assertNotNull(state)
        
        assertEquals(100, state.level)
        assertEquals(true, state.isCharging)
        assertEquals(false, state.isPowerSaveMode)
    }
    
    @Test
    fun testPowerSaveMode() = runTest {
        val isPowerSaveMode = batteryInfoProvider.isPowerSaveMode()
        assertFalse(isPowerSaveMode)
    }
    
    @Test
    fun testCleanup() {
        // Just verify that cleanup doesn't throw any exceptions
        batteryInfoProvider.cleanup()
    }
}