package app.logdate.client.permissions.di

import app.logdate.client.permissions.DesktopPasskeyManager
import app.logdate.client.permissions.DesktopPermissionManager
import app.logdate.client.permissions.PasskeyManager
import app.logdate.client.permissions.PermissionManager
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Desktop-specific permissions module
 */
actual val permissionsModule: Module = module {
    includes(commonPermissionsModule)
    
    // Desktop-specific implementations
    single<PasskeyManager> { DesktopPasskeyManager() }
    
    // Override the PermissionManager with Desktop-specific implementation
    // which always grants permissions
    single<PermissionManager>(override = true) { 
        DesktopPermissionManager() 
    }
}