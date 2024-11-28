package app.logdate.client.intelligence.di

import app.logdate.client.intelligence.cache.AICacheLocalDataSource
import app.logdate.client.intelligence.cache.AndroidAICacheLocalDataSource
import app.logdate.client.networking.di.networkingModule
import org.koin.core.module.Module
import org.koin.dsl.module

actual val cacheModule: Module = module {
    includes(networkingModule)
    factory<AICacheLocalDataSource> { AndroidAICacheLocalDataSource(get()) }
}