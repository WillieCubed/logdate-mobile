package app.logdate.client.permissions.di

import app.logdate.client.permissions.PermissionManager
import app.logdate.client.permissions.createPermissionManager
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Common permissions module that provides platform-specific implementations
 * of permission-related functionality.
 */
val commonPermissionsModule = module {
    // Provide the platform-specific PermissionManager
    single<PermissionManager> { createPermissionManager() }
}

/**
 * Platform-specific permission module
 */
expect val permissionsModule: Module