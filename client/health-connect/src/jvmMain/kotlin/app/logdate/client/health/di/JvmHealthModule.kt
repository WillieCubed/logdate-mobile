package app.logdate.client.health.di

import app.logdate.client.health.datasource.JvmStubRemoteHealthDataSource
import app.logdate.client.health.datasource.RemoteHealthDataSource
import kotlinx.coroutines.Dispatchers
import org.koin.dsl.module
import kotlin.coroutines.CoroutineContext

/**
 * JVM-specific health module that provides platform implementations
 */
val jvmHealthModule = module {
    // JVM-specific remote data source
    single<RemoteHealthDataSource> {
        JvmStubRemoteHealthDataSource()
    }
    
    // JVM-specific IO dispatcher
    single<CoroutineContext>(qualifier = org.koin.core.qualifier.named("io-dispatcher")) {
        Dispatchers.IO
    }
}