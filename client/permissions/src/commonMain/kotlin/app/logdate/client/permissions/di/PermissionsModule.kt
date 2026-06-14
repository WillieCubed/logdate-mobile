package app.logdate.client.permissions.di

import app.logdate.client.permissions.EmailVerificationManager
import app.logdate.client.permissions.GoogleSignInManager
import app.logdate.client.permissions.NoOpGoogleSignInManager
import app.logdate.client.permissions.NoOpRestoreCredentialManager
import app.logdate.client.permissions.PermissionManager
import app.logdate.client.permissions.RestoreCredentialManager
import app.logdate.client.permissions.UnavailableEmailVerificationManager
import app.logdate.client.permissions.createPermissionManager
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Common permissions module that provides platform-specific implementations
 * of permission-related functionality.
 */
val commonPermissionsModule =
    module {
        // Provide the platform-specific PermissionManager
        single<PermissionManager> { createPermissionManager() }

        // Default no-op — Android overrides with AndroidRestoreCredentialManager
        single<RestoreCredentialManager> { NoOpRestoreCredentialManager() }

        // Default unavailable — Android overrides with AndroidEmailVerificationManager
        single<EmailVerificationManager> { UnavailableEmailVerificationManager() }

        // Default no-op — Android overrides with AndroidGoogleSignInManager
        single<GoogleSignInManager> { NoOpGoogleSignInManager() }
    }

/**
 * Platform-specific permission module
 */
expect val permissionsModule: Module
