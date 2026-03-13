package app.logdate.client.location.di

import android.location.Geocoder
import app.logdate.client.location.AndroidLocationProvider
import app.logdate.client.location.ClientLocationProvider
import app.logdate.client.location.history.di.locationHistoryModule
import app.logdate.client.location.places.AndroidReverseGeocodingProvider
import app.logdate.client.location.places.ExternalPlacesProvider
import app.logdate.client.location.places.GooglePlacesExternalPlacesProvider
import app.logdate.client.location.places.ReverseGeocodingProvider
import org.koin.core.module.Module
import org.koin.dsl.module

actual val locationModule: Module =
    module {
        includes(locationTrackerModule)
        includes(locationHistoryModule)
        includes(scheduledLocationModule)
        includes(locationSettingsModule)

        single<ClientLocationProvider> { AndroidLocationProvider(get(), get()) }
        single<ExternalPlacesProvider> { GooglePlacesExternalPlacesProvider(get()) }
        single { Geocoder(get()) }
        single<ReverseGeocodingProvider> { AndroidReverseGeocodingProvider(get()) }
    }
