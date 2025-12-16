package app.logdate.client.device.identity.di

import org.koin.core.module.Module

/**
 * Dependency injection module for device identity components.
 * Platform-specific implementations should provide DeviceManager instances.
 */
expect val deviceIdentityModule: Module