package app.logdate.client.data.account.di

import app.logdate.client.data.account.IosPlatformInfoProvider
import app.logdate.client.data.account.PlatformInfoProvider
import app.logdate.client.data.account.passkey.IosPasskeyManager
import app.logdate.client.domain.account.passkey.PasskeyManager
import org.koin.dsl.module

/**
 * Koin module for iOS-specific account data dependencies.
 *
 * This module provides iOS implementations of platform-specific interfaces
 * needed for account functionality.
 */
val iosAccountDataModule = module {
    // Platform info provider
    single<PlatformInfoProvider> { 
        IosPlatformInfoProvider(
            appInfoProvider = get()
        )
    }
    
    // Passkey manager
    single<PasskeyManager> { 
        IosPasskeyManager()
    }
}