package app.logdate.client.sensor.battery

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSProcessInfoPowerStateDidChangeNotification
import platform.Foundation.lowPowerModeEnabled
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceBatteryLevelDidChangeNotification
import platform.UIKit.UIDeviceBatteryState
import platform.UIKit.UIDeviceBatteryStateDidChangeNotification

class IosBatteryInfoProvider : BatteryInfoProvider {
    
    private val device = UIDevice.currentDevice
    private val processInfo = NSProcessInfo.processInfo
    private val _batteryState = MutableStateFlow(getCurrentBatteryStateInternal())
    private var batteryMonitoringEnabled = false
    private var observers = mutableListOf<Any>()
    
    init {
        // Enable battery monitoring
        device.setBatteryMonitoringEnabled(true)
        batteryMonitoringEnabled = true
        
        // Register for battery notifications
        val notificationCenter = NSNotificationCenter.defaultCenter
        
        // Battery state observer
        val stateObserver = notificationCenter.addObserverForName(
            UIDeviceBatteryStateDidChangeNotification,
            null,
            NSOperationQueue.mainQueue,
            { _ -> _batteryState.value = getCurrentBatteryStateInternal() }
        )
        
        // Battery level observer
        val levelObserver = notificationCenter.addObserverForName(
            UIDeviceBatteryLevelDidChangeNotification,
            null,
            NSOperationQueue.mainQueue,
            { _ -> _batteryState.value = getCurrentBatteryStateInternal() }
        )
        
        // Power state change observer
        val powerStateObserver = notificationCenter.addObserverForName(
            NSProcessInfoPowerStateDidChangeNotification,
            null,
            NSOperationQueue.mainQueue,
            { _ -> _batteryState.value = getCurrentBatteryStateInternal() }
        )
        
        observers.add(stateObserver)
        observers.add(levelObserver)
        observers.add(powerStateObserver)
    }
    
    override val currentBatteryState: Flow<BatteryState> = _batteryState.asStateFlow()
    
    override suspend fun getCurrentBatteryState(): BatteryState {
        return getCurrentBatteryStateInternal()
    }
    
    override suspend fun isPowerSaveMode(): Boolean {
        return processInfo.lowPowerModeEnabled
    }
    
    override fun cleanup() {
        if (batteryMonitoringEnabled) {
            device.setBatteryMonitoringEnabled(false)
            batteryMonitoringEnabled = false
            
            // Remove observers
            val notificationCenter = NSNotificationCenter.defaultCenter
            observers.forEach {
                notificationCenter.removeObserver(it)
            }
            observers.clear()
        }
    }
    
    private fun getCurrentBatteryStateInternal(): BatteryState {
        if (!batteryMonitoringEnabled) {
            device.setBatteryMonitoringEnabled(true)
            batteryMonitoringEnabled = true
        }
        
        val level = (device.batteryLevel.toDouble() * 100).toInt()
        val isCharging = device.batteryState == UIDeviceBatteryState.UIDeviceBatteryStateCharging ||
                device.batteryState == UIDeviceBatteryState.UIDeviceBatteryStateFull
                
        val isPowerSaveMode = processInfo.lowPowerModeEnabled
        
        return BatteryState(
            level = level,
            isCharging = isCharging,
            isPowerSaveMode = isPowerSaveMode
        )
    }
}