package app.logdate.client.device.di

import app.logdate.client.device.AppInfoProvider
import app.logdate.client.device.BuildConfigAppInfoProvider
import app.logdate.client.device.identity.di.deviceIdentityModule
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Main entry point for the device module.
 * Includes all common and platform-specific components.
 */
val deviceModule: Module = module {
    // Include device identity components
    includes(deviceIdentityModule)
    
    // Include device instance module
    includes(deviceInstanceModule)
    
    // Provide the default app info provider if not already provided by platform
    single<AppInfoProvider> { BuildConfigAppInfoProvider() }
}