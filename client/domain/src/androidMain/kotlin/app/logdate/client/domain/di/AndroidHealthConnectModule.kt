package app.logdate.client.domain.di

import app.logdate.client.health.di.androidHealthModule
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android-specific Koin module for health-related dependencies.
 * 
 * @deprecated Use the new healthDomainModule and androidHealthModule for better architecture.
 */
@Deprecated("Use healthDomainModule and androidHealthModule", ReplaceWith("healthDomainModule"))
val androidHealthConnectModule: Module = module {
    // Include the new modules that provide the updated implementation
    includes(healthDomainModule)
    includes(androidHealthModule)
}