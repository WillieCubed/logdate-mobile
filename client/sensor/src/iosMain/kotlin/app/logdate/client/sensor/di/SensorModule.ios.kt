package app.logdate.client.sensor.di

import app.logdate.client.sensor.GyroSensorProvider
import app.logdate.client.sensor.IosGyroSensorProvider
import app.logdate.client.sensor.battery.BatteryInfoProvider
import app.logdate.client.sensor.battery.IosBatteryInfoProvider
import app.logdate.client.sensor.network.IosNetworkSaverModeProvider
import app.logdate.client.sensor.network.NetworkSaverModeProvider
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * iOS-specific implementation of the sensor module.
 */
actual val sensorModule: Module = module {
    single<GyroSensorProvider> {
        IosGyroSensorProvider(
            coroutineScope = get()
        )
    }
    
    single<BatteryInfoProvider> {
        IosBatteryInfoProvider() 
    }
    
    single<NetworkSaverModeProvider> { 
        IosNetworkSaverModeProvider() 
    }
}