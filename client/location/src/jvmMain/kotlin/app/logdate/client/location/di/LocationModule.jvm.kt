package app.logdate.client.location.di

import app.logdate.client.location.ClientLocationProvider
import app.logdate.client.location.DesktopLocationProvider
import app.logdate.client.location.places.ExternalPlacesProvider
import app.logdate.client.location.places.StubExternalPlacesProvider
import org.koin.core.module.Module
import org.koin.dsl.module
import java.net.InetAddress

actual val locationModule: Module = module {

    single<ClientLocationProvider> { DesktopLocationProvider() }
    single<ExternalPlacesProvider> { StubExternalPlacesProvider() }
    
    // Device ID for location tracking
    single { 
        val hostname = try {
            InetAddress.getLocalHost().hostName
        } catch (e: Exception) {
            "unknown"
        }
        "desktop_$hostname" 
    }
}