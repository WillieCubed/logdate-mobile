package app.logdate.client.location.history.di

import app.logdate.client.location.history.LocationTracker
import app.logdate.client.location.history.StandardLocationTracker
import org.koin.core.module.Module
import org.koin.dsl.module
import java.net.InetAddress

/**
 * Desktop-specific Koin module for location history.
 */
actual val locationHistoryModule: Module = module {
    // Provide a default device ID for desktop
    single {
        val hostname = try {
            InetAddress.getLocalHost().hostName
        } catch (e: Exception) {
            "unknown"
        }
        
        // Return the device ID using the hostname
        "desktop_$hostname"
    }
    
    // Provide the LocationTracker implementation
    single<LocationTracker> {
        StandardLocationTracker(
            locationProvider = get(),
            locationHistoryRepository = get(),
            deviceId = get()
        )
    }
}