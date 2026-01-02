package app.logdate.client.intelligence.di

import app.logdate.client.intelligence.cache.AICacheLocalDataSource
import app.logdate.client.intelligence.cache.AICacheConfig
import app.logdate.client.intelligence.cache.AndroidAICacheLocalDataSource
import app.logdate.client.intelligence.cache.GenerativeAICache
import app.logdate.client.intelligence.cache.OfflineGenerativeAICache
import app.logdate.client.networking.di.networkingModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val cacheModule: Module = module {
    includes(networkingModule)
    factory<AICacheLocalDataSource> {
        val cacheDir = androidContext().cacheDir.absolutePath
        AndroidAICacheLocalDataSource(cacheDir)
    }
    single { cacheConfigFromProperties() }
    single<GenerativeAICache> { OfflineGenerativeAICache(get(), config = get()) }
}

private fun cacheConfigFromProperties(): AICacheConfig {
    val defaults = AICacheConfig()
    val koin = org.koin.core.context.GlobalContext.get()
    val memoryEntries = koin.getProperty<String>("AI_CACHE_MEMORY_MAX_ENTRIES")?.toIntOrNull()
    val memoryBytes = koin.getProperty<String>("AI_CACHE_MEMORY_MAX_BYTES")?.toLongOrNull()
    val persistentEntries = koin.getProperty<String>("AI_CACHE_PERSISTENT_MAX_ENTRIES")?.toIntOrNull()
    val persistentBytes = koin.getProperty<String>("AI_CACHE_PERSISTENT_MAX_BYTES")?.toLongOrNull()
    return AICacheConfig(
        memoryMaxEntries = memoryEntries ?: defaults.memoryMaxEntries,
        memoryMaxBytes = memoryBytes ?: defaults.memoryMaxBytes,
        persistentMaxEntries = persistentEntries ?: defaults.persistentMaxEntries,
        persistentMaxBytes = persistentBytes ?: defaults.persistentMaxBytes
    )
}
