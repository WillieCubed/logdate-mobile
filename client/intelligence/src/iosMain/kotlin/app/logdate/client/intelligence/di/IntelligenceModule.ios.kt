package app.logdate.client.intelligence.di

import app.logdate.client.intelligence.cache.AICacheLocalDataSource
import app.logdate.client.intelligence.cache.IOSAICacheLocalDataSource
import org.koin.core.module.Module
import org.koin.dsl.module

actual val cacheModule: Module = module {
    factory<AICacheLocalDataSource> { IOSAICacheLocalDataSource(get()) }
}