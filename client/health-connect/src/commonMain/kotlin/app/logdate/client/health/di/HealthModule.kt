package app.logdate.client.health.di

import app.logdate.client.health.DefaultLocalFirstHealthRepository
import app.logdate.client.health.HealthDataRepository
import app.logdate.client.health.LocalFirstHealthRepository
import app.logdate.client.health.datasource.InMemoryLocalHealthDataSource
import app.logdate.client.health.datasource.LocalHealthDataSource
import org.koin.dsl.module

/**
 * IO Dispatcher qualifier for dependency injection
 */
private val IODispatcher =
    org.koin.core.qualifier
        .named("io-dispatcher")

/**
 * Common health module that's the same across all platforms
 * Platform-specific data sources are provided in platform modules
 */
val healthModule =
    module {
        // Local data source (same implementation for all platforms)
        single<LocalHealthDataSource> {
            InMemoryLocalHealthDataSource()
        }

        // The day-boundary preference contract (LogdatePreferencesDataSource) is bound by the
        // domain module, which adapts the persisted datastore preferences. This module cannot
        // depend on the datastore directly.

        // Main repository
        single<LocalFirstHealthRepository> {
            DefaultLocalFirstHealthRepository(
                localDataSource = get(),
                remoteDataSource = get(),
                preferencesDataSource = get(),
                ioDispatcher = get(IODispatcher),
            )
        }

        // Also provide as HealthDataRepository for backward compatibility
        single<HealthDataRepository> {
            get<LocalFirstHealthRepository>()
        }
    }
