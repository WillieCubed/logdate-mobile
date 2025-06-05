package app.logdate.client.sensor.di

import app.logdate.client.sensor.battery.BatteryInfoProvider
import app.logdate.client.sensor.battery.DesktopBatteryInfoProvider
import app.logdate.client.sensor.network.DesktopNetworkSaverModeProvider
import app.logdate.client.sensor.network.NetworkSaverModeProvider
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Desktop-specific implementation of the sensor module.
 */
actual val sensorModule: Module = module {
    includes(commonSensorModule)
    
    // Override with Desktop-specific implementations
    single<BatteryInfoProvider> { 
        DesktopBatteryInfoProvider() 
    }
    
    single<NetworkSaverModeProvider> { 
        DesktopNetworkSaverModeProvider() 
    }
}