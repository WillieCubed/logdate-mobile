package app.logdate.client.permissions.di

import app.logdate.client.permissions.AndroidEmailVerificationManager
import app.logdate.client.permissions.AndroidGoogleSignInManager
import app.logdate.client.permissions.AndroidPasskeyManager
import app.logdate.client.permissions.AndroidPermissionManager
import app.logdate.client.permissions.AndroidRestoreCredentialManager
import app.logdate.client.permissions.EmailVerificationManager
import app.logdate.client.permissions.GoogleSignInManager
import app.logdate.client.permissions.PasskeyManager
import app.logdate.client.permissions.PermissionManager
import app.logdate.client.permissions.RestoreCredentialManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android-specific permissions module
 */
actual val permissionsModule: Module =
    module {
        includes(commonPermissionsModule)

        // Android-specific implementations
        single<PasskeyManager> { AndroidPasskeyManager(androidContext()) }
        single<RestoreCredentialManager> { AndroidRestoreCredentialManager(androidContext()) }
        single<EmailVerificationManager> { AndroidEmailVerificationManager(androidContext(), get()) }
        single<GoogleSignInManager> { AndroidGoogleSignInManager(androidContext()) }

        // Override the PermissionManager with Android-specific implementation
        single<PermissionManager>(createdAtStart = true) {
            AndroidPermissionManager(androidContext())
        }
    }
