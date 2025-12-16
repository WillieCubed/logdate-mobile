package app.logdate.client.location.history.di

import app.logdate.client.location.history.LocationTracker
import app.logdate.client.location.history.StandardLocationTracker
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android-specific Koin module for location history.
 */
actual val locationHistoryModule: Module = module {

    // First provide the factory for the LocationTracker interface
    factory { (locationHistoryRepository: app.logdate.client.repository.location.LocationHistoryRepository) ->
        StandardLocationTracker(
            locationProvider = get(),
            locationHistoryRepository = locationHistoryRepository,
            deviceId = get()
        )
    }

    // Then create the singleton instance after dataModule is initialized
    single<LocationTracker> {
        get<(app.logdate.client.repository.location.LocationHistoryRepository) -> LocationTracker>()(
            get()
        )
    }
}