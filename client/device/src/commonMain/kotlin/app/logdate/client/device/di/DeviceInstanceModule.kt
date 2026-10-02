package app.logdate.client.device.di

import org.koin.core.module.Module

/**
 * Platform bindings for the device module, including the platform's own
 * [app.logdate.client.device.AppInfoProvider]. Shared modules must not bind that provider again,
 * because the binding that loads last replaces this one.
 */
expect val deviceInstanceModule: Module
