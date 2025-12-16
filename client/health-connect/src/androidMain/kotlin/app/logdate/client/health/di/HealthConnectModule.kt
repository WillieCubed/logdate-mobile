package app.logdate.client.health.di

import app.logdate.client.health.AndroidSleepRepository
import app.logdate.client.health.SleepRepository
import app.logdate.client.health.datasource.AndroidHealthConnectDataSource
import app.logdate.client.health.datasource.RemoteHealthDataSource
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android-specific Koin module for health connect components.
 * 
 * @deprecated This module is being replaced by androidHealthModule which provides
 * implementations for the new LocalFirstHealthRepository architecture.
 */
@Deprecated("Use androidHealthModule instead", ReplaceWith("androidHealthModule"))
val healthConnectModule: Module = module {
    // Only keep the SleepRepository that might be used by legacy code
    single<SleepRepository> {
        AndroidSleepRepository(androidContext())
    }
    
    // Add the Android-specific remote data source
    single<RemoteHealthDataSource> {
        AndroidHealthConnectDataSource(androidContext())
    }
}