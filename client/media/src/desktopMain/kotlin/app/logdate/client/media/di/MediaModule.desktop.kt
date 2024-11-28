package app.logdate.client.media.di

import app.logdate.client.media.DesktopMediaManager
import app.logdate.client.media.MediaManager
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Module that exposes handles for interacting with OS-specific media library APIs.
 */
actual val mediaModule: Module = module {
    single<MediaManager> { DesktopMediaManager() }
}