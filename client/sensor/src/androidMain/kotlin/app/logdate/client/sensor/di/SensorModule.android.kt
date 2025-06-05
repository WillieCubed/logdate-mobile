package app.logdate.client.sensor.di

import app.logdate.client.sensor.AndroidGyroSensorProvider
import app.logdate.client.sensor.GyroSensorProvider
import app.logdate.client.sensor.battery.AndroidBatteryInfoProvider
import app.logdate.client.sensor.battery.BatteryInfoProvider
import app.logdate.client.sensor.network.AndroidNetworkSaverModeProvider
import app.logdate.client.sensor.network.NetworkSaverModeProvider
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android-specific implementation of the sensor module.
 */
actual val sensorModule: Module = module {
    single<GyroSensorProvider> {
        AndroidGyroSensorProvider(
            coroutineScope = get(),
            context = androidContext()
        ) 
    }
    
    single<BatteryInfoProvider> { 
        AndroidBatteryInfoProvider(
            context = androidContext()
        ) 
    }
    
    single<NetworkSaverModeProvider> { 
        AndroidNetworkSaverModeProvider(
            context = androidContext()
        ) 
    }
}