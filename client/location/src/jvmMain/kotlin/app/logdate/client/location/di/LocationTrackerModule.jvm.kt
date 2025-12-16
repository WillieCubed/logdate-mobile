package app.logdate.client.location.di

import app.logdate.client.location.DeviceLocationTracker
import app.logdate.client.location.DesktopLocationProvider
import app.logdate.client.location.JvmDeviceLocationTracker
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * JVM/Desktop-specific Koin module for device location tracking.
 */
actual val locationTrackerModule: Module = module {
    // Provide the desktop implementation of DeviceLocationTracker
    single<DeviceLocationTracker> {
        JvmDeviceLocationTracker(
            locationProvider = get<DesktopLocationProvider>()
        )
    }
}