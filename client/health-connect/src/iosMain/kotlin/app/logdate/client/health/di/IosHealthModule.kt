package app.logdate.client.health.di

import app.logdate.client.health.datasource.IosHealthKitDataSource
import app.logdate.client.health.datasource.RemoteHealthDataSource
import kotlinx.coroutines.Dispatchers
import org.koin.dsl.module
import kotlin.coroutines.CoroutineContext

/**
 * iOS-specific health module that provides platform implementations
 */
val iosHealthModule = module {
    // iOS-specific remote data source
    single<RemoteHealthDataSource> {
        IosHealthKitDataSource()
    }
    
    // iOS-specific IO dispatcher
    single<CoroutineContext>(qualifier = org.koin.core.qualifier.named("io-dispatcher")) {
        Dispatchers.Default
    }
}