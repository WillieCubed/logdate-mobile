package app.logdate.client.media.di

import android.content.ContentResolver
import app.logdate.client.media.AndroidMediaManager
import app.logdate.client.media.MediaManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Module that exposes handles for interacting with OS-specific media library APIs.
 */
actual val mediaModule: Module = module {
    single<ContentResolver> { androidContext().contentResolver }
    single<MediaManager> { AndroidMediaManager(get(), get()) }
}