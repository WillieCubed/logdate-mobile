package app.logdate.client.health.di

import app.logdate.client.health.IosSleepRepository
import app.logdate.client.health.SleepRepository
import app.logdate.client.health.datasource.IosHealthKitDataSource
import app.logdate.client.health.datasource.RemoteHealthDataSource
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * iOS-specific Koin module for health connect components.
 * 
 * @deprecated This module is being replaced by iosHealthModule which provides
 * implementations for the new LocalFirstHealthRepository architecture.
 */
@Deprecated("Use iosHealthModule instead", ReplaceWith("iosHealthModule"))
val iosHealthConnectModule: Module = module {
    // Only keep the SleepRepository that might be used by legacy code
    single<SleepRepository> {
        IosSleepRepository()
    }
    
    // Add the iOS-specific remote data source
    single<RemoteHealthDataSource> {
        IosHealthKitDataSource()
    }
}