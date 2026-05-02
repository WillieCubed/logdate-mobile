package app.logdate.client.sharing.di

import app.logdate.client.sharing.IosSharingLauncher
import app.logdate.client.sharing.NoOpRewindQuoteCardRenderer
import app.logdate.client.sharing.NoOpRewindStatsSummaryRenderer
import app.logdate.client.sharing.RewindQuoteCardRenderer
import app.logdate.client.sharing.RewindStatsSummaryRenderer
import app.logdate.client.sharing.SharingLauncher
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Module that exposes handles for sharing content outside of the app for iOS platforms.
 */
actual val sharingModule: Module =
    module {
        single<SharingLauncher> {
            IosSharingLauncher(
                journalRepository = get(),
                mediaManager = get(),
            )
        }
        single<RewindQuoteCardRenderer> { NoOpRewindQuoteCardRenderer }
        single<RewindStatsSummaryRenderer> { NoOpRewindStatsSummaryRenderer }
    }
