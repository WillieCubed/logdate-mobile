package app.logdate.client.domain.di

import app.logdate.client.health.di.iosHealthModule
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * iOS-specific Koin module for health-related dependencies.
 * 
 * @deprecated Use the new healthDomainModule and iosHealthModule for better architecture.
 */
@Deprecated("Use healthDomainModule and iosHealthModule", ReplaceWith("healthDomainModule"))
val iosHealthConnectModule: Module = module {
    // Include the new modules that provide the updated implementation
    includes(healthDomainModule)
    includes(iosHealthModule)
}