package app.logdate.client.sharing.di

import app.logdate.client.sharing.AndroidShareAssetGenerator
import app.logdate.client.sharing.AndroidSharingLauncher
import app.logdate.client.sharing.ShareAssetInterface
import app.logdate.client.sharing.SharingLauncher
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Module that exposes handles for sharing content outside of the app.
 */
actual val sharingModule: Module = module {
    factory<ShareAssetInterface> { AndroidShareAssetGenerator(get()) }
    factory<SharingLauncher> { AndroidSharingLauncher(get(), get(), get(), get()) }
}