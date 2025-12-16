package app.logdate.client.health.di

import app.logdate.client.health.data.DesktopLocalHealthDataSource
import app.logdate.client.health.data.RemoteHealthDataSource
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Desktop-specific Koin module for health data dependencies.
 */
val desktopHealthModule: Module = module {
    // Desktop implementation of remote data source
    single<RemoteHealthDataSource> { 
        DesktopLocalHealthDataSource()
    }
}