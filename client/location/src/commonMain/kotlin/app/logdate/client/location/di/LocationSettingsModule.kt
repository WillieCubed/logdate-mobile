package app.logdate.client.location.di

import app.logdate.client.location.settings.DefaultLocationTrackingSettingsRepository
import app.logdate.client.location.settings.LocationTrackingSettingsRepository
import org.koin.dsl.module

/**
 * Koin module for location tracking settings.
 */
val locationSettingsModule = module {
    single<LocationTrackingSettingsRepository> { 
        DefaultLocationTrackingSettingsRepository(get()) 
    }
}