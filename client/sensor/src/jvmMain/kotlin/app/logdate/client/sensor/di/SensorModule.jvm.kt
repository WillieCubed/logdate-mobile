package app.logdate.client.sensor.di

import app.logdate.client.sensor.battery.BatteryInfoProvider
import app.logdate.client.sensor.battery.JvmBatteryInfoProvider
import app.logdate.client.sensor.network.JvmNetworkSaverModeProvider
import app.logdate.client.sensor.network.NetworkSaverModeProvider
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * JVM-specific implementation of the sensor module.
 */
actual val sensorModule: Module = module {
    single<BatteryInfoProvider> {
        JvmBatteryInfoProvider() 
    }
    
    single<NetworkSaverModeProvider> { 
        JvmNetworkSaverModeProvider() 
    }
}