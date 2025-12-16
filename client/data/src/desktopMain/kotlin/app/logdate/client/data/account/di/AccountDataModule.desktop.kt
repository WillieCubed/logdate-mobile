package app.logdate.client.data.account.di

import app.logdate.client.data.account.DesktopPlatformInfoProvider
import app.logdate.client.data.account.PlatformInfoProvider
import app.logdate.client.data.account.passkey.DesktopPasskeyManager
import app.logdate.client.domain.account.passkey.PasskeyManager
import org.koin.dsl.module

/**
 * Koin module for desktop-specific account data dependencies.
 *
 * This module provides desktop implementations of platform-specific interfaces
 * needed for account functionality.
 */
val desktopAccountDataModule = module {
    // Platform info provider
    single<PlatformInfoProvider> { 
        DesktopPlatformInfoProvider(
            appInfoProvider = get()
        )
    }
    
    // Passkey manager
    single<PasskeyManager> { 
        DesktopPasskeyManager()
    }
}