package app.logdate.client.sharing.di

import app.logdate.client.sharing.DesktopSharingLauncher
import app.logdate.client.sharing.SharingLauncher
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Module that exposes handles for sharing content outside of the app for desktop platforms.
 */
actual val sharingModule: Module = module {
    single<SharingLauncher> { 
        DesktopSharingLauncher(
            journalRepository = get()
        ) 
    }
}