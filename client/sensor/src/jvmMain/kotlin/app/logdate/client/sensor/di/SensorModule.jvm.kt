package app.logdate.client.sensor.di

import app.logdate.client.networking.saver.NetworkSaverModeProvider
import app.logdate.client.sensor.GyroSensorProvider
import app.logdate.client.sensor.UnavailableGyroSensorProvider
import app.logdate.client.sensor.battery.BatteryInfoProvider
import app.logdate.client.sensor.battery.JvmBatteryInfoProvider
import app.logdate.client.sensor.network.JvmNetworkSaverModeProvider
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * JVM-specific implementation of the sensor module. Desktop inherits from this source set
 * (see `desktopMain` in `client/sensor/build.gradle.kts`), so this is the single source of
 * truth for JVM/desktop sensor bindings.
 */
actual val sensorModule: Module =
    module {
        single<BatteryInfoProvider> {
            JvmBatteryInfoProvider()
        }

        single<NetworkSaverModeProvider> {
            JvmNetworkSaverModeProvider()
        }

        // No CoreMotion/SensorManager equivalent on the JVM; emit nothing.
        single<GyroSensorProvider> {
            UnavailableGyroSensorProvider(coroutineScope = get())
        }
    }
