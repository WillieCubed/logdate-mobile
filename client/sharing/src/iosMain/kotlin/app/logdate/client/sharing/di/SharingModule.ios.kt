package app.logdate.client.sharing.di

import app.logdate.client.sharing.IosSharingLauncher
import app.logdate.client.sharing.SharingLauncher
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Module that exposes handles for sharing content outside of the app for iOS platforms.
 */
actual val sharingModule: Module = module {
    single<SharingLauncher> { 
        IosSharingLauncher(
            journalRepository = get()
        ) 
    }
}