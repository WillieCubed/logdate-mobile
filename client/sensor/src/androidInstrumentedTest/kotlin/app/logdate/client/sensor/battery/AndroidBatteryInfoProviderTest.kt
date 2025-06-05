package app.logdate.client.sensor.battery

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertNotNull

class AndroidBatteryInfoProviderTest {
    
    private lateinit var context: Context
    private lateinit var batteryInfoProvider: AndroidBatteryInfoProvider
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        batteryInfoProvider = AndroidBatteryInfoProvider(context)
    }
    
    @Test
    fun testBatteryStateNotNull() = runTest {
        val batteryState = batteryInfoProvider.getCurrentBatteryState()
        assertNotNull(batteryState)
        assertNotNull(batteryState.level)
        // Level should be between 0-100
        assert(batteryState.level in 0..100)
    }
    
    @Test
    fun testBatteryStateFlow() = runTest {
        val batteryState = batteryInfoProvider.currentBatteryState.first()
        assertNotNull(batteryState)
    }
    
    @Test
    fun testPowerSaveModeIsBoolean() = runTest {
        val isPowerSaveMode = batteryInfoProvider.isPowerSaveMode()
        // Just verify it returns a boolean value without error
        assertNotNull(isPowerSaveMode)
    }
}