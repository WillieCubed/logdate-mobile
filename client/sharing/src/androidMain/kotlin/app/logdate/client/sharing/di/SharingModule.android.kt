package app.logdate.client.sharing.di

import app.logdate.client.media.di.mediaModule
import app.logdate.client.sharing.AndroidRewindQuoteCardRenderer
import app.logdate.client.sharing.AndroidRewindStatsSummaryRenderer
import app.logdate.client.sharing.AndroidShareAssetGenerator
import app.logdate.client.sharing.AndroidSharingLauncher
import app.logdate.client.sharing.NoOpRewindPanelCardRenderer
import app.logdate.client.sharing.RewindPanelCardRenderer
import app.logdate.client.sharing.RewindQuoteCardRenderer
import app.logdate.client.sharing.RewindStatsSummaryRenderer
import app.logdate.client.sharing.ShareAssetInterface
import app.logdate.client.sharing.SharingLauncher
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Module that exposes handles for sharing content outside of the app.
 */
actual val sharingModule: Module =
    module {
        includes(mediaModule)
        factory<ShareAssetInterface> { AndroidShareAssetGenerator(androidContext(), get(named("io-dispatcher"))) }
        factory<RewindQuoteCardRenderer> { AndroidRewindQuoteCardRenderer(androidContext(), get(named("io-dispatcher"))) }
        factory<RewindPanelCardRenderer> { NoOpRewindPanelCardRenderer }
        factory<RewindStatsSummaryRenderer> {
            AndroidRewindStatsSummaryRenderer(androidContext(), get(named("io-dispatcher")))
        }
        factory<SharingLauncher> { AndroidSharingLauncher(get(), get(), get(), get()) }
    }
