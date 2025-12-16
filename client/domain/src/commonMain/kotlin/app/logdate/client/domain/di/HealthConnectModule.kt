package app.logdate.client.domain.di

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin module for health connect domain layer dependencies.
 * 
 * @deprecated This module is being replaced by the healthDomainModule.
 * New code should use the healthDomainModule instead.
 */
@Deprecated("Use healthDomainModule instead", ReplaceWith("healthDomainModule"))
val healthConnectModule: Module = module {
    // Include just the health domain module
    includes(healthDomainModule)
}