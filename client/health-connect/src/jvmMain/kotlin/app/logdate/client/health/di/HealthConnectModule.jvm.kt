package app.logdate.client.health.di

import app.logdate.client.health.JvmSleepRepository
import app.logdate.client.health.SleepRepository
import app.logdate.client.health.datasource.JvmStubRemoteHealthDataSource
import app.logdate.client.health.datasource.RemoteHealthDataSource
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * JVM/Desktop-specific Koin module for health connect components.
 * 
 * @deprecated This module is being replaced by jvmHealthModule which provides
 * implementations for the new LocalFirstHealthRepository architecture.
 */
@Deprecated("Use jvmHealthModule instead", ReplaceWith("jvmHealthModule"))
val jvmHealthConnectModule: Module = module {
    // Only keep the SleepRepository that might be used by legacy code
    single<SleepRepository> {
        JvmSleepRepository()
    }
    
    // Add the JVM-specific remote data source
    single<RemoteHealthDataSource> {
        JvmStubRemoteHealthDataSource()
    }
}