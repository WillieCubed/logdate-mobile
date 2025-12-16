package app.logdate.client.location.di

import app.logdate.client.location.AndroidDeviceLocationTracker
import app.logdate.client.location.DeviceLocationTracker
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android-specific Koin module for device location tracking.
 */
actual val locationTrackerModule: Module = module {
    single<DeviceLocationTracker> {
        AndroidDeviceLocationTracker(
            context = androidContext(),
            locationProvider = get()
        )
    }
}