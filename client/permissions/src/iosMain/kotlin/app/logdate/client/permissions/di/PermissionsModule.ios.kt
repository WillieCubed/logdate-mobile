package app.logdate.client.permissions.di

import app.logdate.client.permissions.IosPasskeyManager
import app.logdate.client.permissions.IosPermissionManager
import app.logdate.client.permissions.PasskeyManager
import app.logdate.client.permissions.PermissionManager
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * iOS-specific permissions module
 */
actual val permissionsModule: Module = module {
    includes(commonPermissionsModule)
    
    // iOS-specific implementations
    single<PasskeyManager> { IosPasskeyManager() }
    
    // Override the PermissionManager with iOS-specific implementation
    single<PermissionManager>(createdAtStart = true) { 
        IosPermissionManager() 
    }
}