package app.logdate.client.intelligence.di

import app.logdate.client.intelligence.cache.AICacheLocalDataSource
import app.logdate.client.intelligence.cache.GenerativeAICache
import app.logdate.client.intelligence.cache.JvmAICacheLocalDataSource
import app.logdate.client.intelligence.cache.OfflineGenerativeAICache
import org.koin.core.module.Module
import org.koin.dsl.module

actual val cacheModule: Module = module {
    single<GenerativeAICache> { OfflineGenerativeAICache(get()) }
    single<AICacheLocalDataSource> { JvmAICacheLocalDataSource() }
}
