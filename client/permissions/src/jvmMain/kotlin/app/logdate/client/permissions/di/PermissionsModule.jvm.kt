package app.logdate.client.permissions.di

import app.logdate.client.permissions.DesktopPasskeyManager
import app.logdate.client.permissions.JvmPermissionManager
import app.logdate.client.permissions.PasskeyManager
import app.logdate.client.permissions.PermissionManager
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * JVM-specific permissions module
 */
actual val permissionsModule: Module = module {
    includes(commonPermissionsModule)
    
    // JVM-specific implementations
    single<PasskeyManager> { DesktopPasskeyManager() }  // Reuse desktop implementation
    
    // Override the PermissionManager with JVM-specific implementation
    single<PermissionManager>(createdAtStart = true) { 
        JvmPermissionManager() 
    }
}