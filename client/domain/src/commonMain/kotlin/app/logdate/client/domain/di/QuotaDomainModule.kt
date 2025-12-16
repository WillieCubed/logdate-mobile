package app.logdate.client.domain.di

import app.logdate.client.domain.quota.ObserveCloudQuotaUseCase
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Module for quota-related domain use cases.
 * Separated to avoid circular dependencies with sync modules.
 */
val quotaDomainModule: Module = module {
    factory { ObserveCloudQuotaUseCase(get()) }
}