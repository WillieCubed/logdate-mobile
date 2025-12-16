package app.logdate.client.location.di

import app.logdate.client.location.ClientLocationProvider
import app.logdate.client.location.IosLocationProvider
import app.logdate.client.location.history.di.locationHistoryModule
import app.logdate.client.location.places.ExternalPlacesProvider
import app.logdate.client.location.places.StubExternalPlacesProvider
import org.koin.core.module.Module
import org.koin.dsl.module

actual val locationModule: Module = module {
    includes(locationTrackerModule)
    includes(locationHistoryModule)

    single<ClientLocationProvider> { IosLocationProvider() }
    single<ExternalPlacesProvider> { StubExternalPlacesProvider() }
}