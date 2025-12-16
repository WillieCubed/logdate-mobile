package app.logdate.client.sync.di

import app.logdate.client.sync.cloud.di.cloudAccountModule
import app.logdate.shared.model.CloudQuotaManager
import app.logdate.client.sync.quota.LogDateCloudQuotaManager
import app.logdate.client.sync.quota.LogDateQuotaCalculator
import app.logdate.client.sync.quota.QuotaCalculator
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Common quota dependencies shared across all platforms.
 */
val quotaModule = module {
    single<QuotaCalculator> { LogDateQuotaCalculator(get()) }
    single<CloudQuotaManager> { LogDateCloudQuotaManager(get(), get()) }
}

/**
 * Include this module to get access to all sync-related modules.
 */
val allSyncModules = module {
    includes(quotaModule, cloudAccountModule)
}

/**
 * A module for all sync-related dependencies.
 */
expect val syncModule: Module