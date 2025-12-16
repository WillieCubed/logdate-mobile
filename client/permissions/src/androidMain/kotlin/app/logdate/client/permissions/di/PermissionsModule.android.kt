package app.logdate.client.permissions.di

import app.logdate.client.permissions.AndroidPasskeyManager
import app.logdate.client.permissions.AndroidPermissionManager
import app.logdate.client.permissions.PasskeyManager
import app.logdate.client.permissions.PermissionManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android-specific permissions module
 */
actual val permissionsModule: Module = module {
    includes(commonPermissionsModule)
    
    // Android-specific implementations
    single<PasskeyManager> { AndroidPasskeyManager(androidContext()) }
    
    // Override the PermissionManager with Android-specific implementation
    single<PermissionManager>(createdAtStart = true) { 
        AndroidPermissionManager(androidContext()) 
    }
}