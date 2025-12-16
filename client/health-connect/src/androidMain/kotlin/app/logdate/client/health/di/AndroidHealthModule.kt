package app.logdate.client.health.di

import app.logdate.client.health.datasource.AndroidHealthConnectDataSource
import app.logdate.client.health.datasource.RemoteHealthDataSource
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import kotlin.coroutines.CoroutineContext

/**
 * Android-specific health module that provides platform implementations
 */
val androidHealthModule = module {
    // Android-specific remote data source
    single<RemoteHealthDataSource> {
        AndroidHealthConnectDataSource(androidContext())
    }
    
    // Android-specific IO dispatcher
    single<CoroutineContext>(qualifier = org.koin.core.qualifier.named("io-dispatcher")) {
        Dispatchers.IO
    }
}