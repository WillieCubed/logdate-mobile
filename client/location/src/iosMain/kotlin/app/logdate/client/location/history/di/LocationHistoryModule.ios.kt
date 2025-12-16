package app.logdate.client.location.history.di

import app.logdate.client.location.history.LocationTracker
import app.logdate.client.location.history.StandardLocationTracker
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * iOS-specific Koin module for location history.
 */
actual val locationHistoryModule: Module = module {
    single<LocationTracker> {
        StandardLocationTracker(
            locationProvider = get(),
            locationHistoryRepository = get(),
            deviceId = get()
        )
    }
}