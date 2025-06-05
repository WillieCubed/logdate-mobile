package app.logdate.client.sensor.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidBatteryInfoProvider(
    private val context: Context
) : BatteryInfoProvider {
    private val powerManager = context.getSystemService(PowerManager::class.java)
    private val batteryManager = context.getSystemService(BatteryManager::class.java)
    private val _batteryState = MutableStateFlow(getCurrentBatteryStateInternal())
    
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            _batteryState.value = getCurrentBatteryStateInternal()
        }
    }
    
    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        context.registerReceiver(batteryReceiver, filter)
    }
    
    override val currentBatteryState: Flow<BatteryState> = _batteryState.asStateFlow()
    
    override suspend fun getCurrentBatteryState(): BatteryState {
        return _batteryState.value
    }
    
    override suspend fun isPowerSaveMode(): Boolean {
        return powerManager.isPowerSaveMode
    }
    
    override fun cleanup() {
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered, ignore
        }
    }
    
    private fun getCurrentBatteryStateInternal(): BatteryState {
        val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val isCharging = batteryManager.isCharging
        val isPowerSaveMode = powerManager.isPowerSaveMode
        
        return BatteryState(
            level = level,
            isCharging = isCharging,
            isPowerSaveMode = isPowerSaveMode
        )
    }
}