package app.logdate.client.health.di

import app.logdate.client.health.datasource.JvmUnavailableRemoteHealthDataSource
import app.logdate.client.health.datasource.RemoteHealthDataSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.dsl.module

/**
 * JVM-specific health module that provides platform implementations
 */
val jvmHealthModule =
    module {
        // JVM-specific remote data source
        single<RemoteHealthDataSource> {
            JvmUnavailableRemoteHealthDataSource()
        }

        // JVM-specific IO dispatcher
        single<CoroutineDispatcher>(
            qualifier =
                org.koin.core.qualifier
                    .named("io-dispatcher"),
        ) {
            Dispatchers.IO
        }
    }
