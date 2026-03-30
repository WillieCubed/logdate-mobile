package app.logdate.feature.postcards.di

import org.koin.core.module.Module

/**
 * Platform-specific Koin module providing [ExportEngine] and other
 * platform-dependent postcards dependencies.
 */
expect val postcardsPlatformModule: Module
