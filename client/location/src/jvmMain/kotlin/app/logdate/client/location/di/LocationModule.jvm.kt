package app.logdate.client.location.di

import app.logdate.client.location.ClientLocationProvider
import app.logdate.client.location.DesktopLocationProvider
import app.logdate.client.location.places.ExternalPlacesProvider
import app.logdate.client.location.places.ReverseGeocodingProvider
import app.logdate.client.location.places.UnavailableExternalPlacesProvider
import app.logdate.client.location.places.UnavailableReverseGeocodingProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.net.InetAddress

private val desktopLocationScopeQualifier = named("desktop-location-scope")

actual val locationModule: Module =
    module {
        includes(locationSettingsModule)

        single<CoroutineScope>(desktopLocationScopeQualifier) {
            CoroutineScope(SupervisorJob() + Dispatchers.Default)
        }
        single { DesktopLocationProvider(get(), get(desktopLocationScopeQualifier)) }
        single<ClientLocationProvider> { get<DesktopLocationProvider>() }
        single<ExternalPlacesProvider> { UnavailableExternalPlacesProvider() }
        single<ReverseGeocodingProvider> { UnavailableReverseGeocodingProvider() }

        // Device ID for location tracking
        single {
            val hostname =
                try {
                    InetAddress.getLocalHost().hostName
                } catch (e: Exception) {
                    "unknown"
                }
            "desktop_$hostname"
        }
    }
