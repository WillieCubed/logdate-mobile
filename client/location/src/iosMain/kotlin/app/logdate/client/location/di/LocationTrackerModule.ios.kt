package app.logdate.client.location.di

import app.logdate.client.location.DeviceLocationTracker
import app.logdate.client.location.IosDeviceLocationTracker
import app.logdate.client.location.IosLocationProvider
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * iOS-specific Koin module for device location tracking.
 */
actual val locationTrackerModule: Module = module {
    
    // Provide the iOS implementation of DeviceLocationTracker
    single<DeviceLocationTracker> {
        IosDeviceLocationTracker(
            locationProvider = get<IosLocationProvider>()
        )
    }
}