package app.logdate.client.media.di

import android.content.ContentResolver
import app.logdate.client.media.AndroidMediaCleaner
import app.logdate.client.media.AndroidMediaManager
import app.logdate.client.media.MediaCleaner
import app.logdate.client.media.MediaManager
import app.logdate.client.media.audio.transcription.AndroidTranscriptionManager
import app.logdate.client.media.audio.transcription.TranscriptionManager
import app.logdate.client.media.display.OnDemandRemoteDisplayManager
import app.logdate.client.media.display.RemoteDisplayManager
import app.logdate.client.media.video.ExoPlayerPool
import app.logdate.client.media.video.MediaCache
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Module that exposes handles for interacting with OS-specific media library APIs.
 *
 * Note: We don't include audioModule here anymore to avoid circular dependencies.
 */
actual val mediaModule: Module =
    module {
        // Media manager dependencies
        single<ContentResolver> { androidContext().contentResolver }
        single<MediaManager> { AndroidMediaManager(get(), get()) }
        single<MediaCleaner> { AndroidMediaCleaner() }

        // Transcription manager
        single<TranscriptionManager> { AndroidTranscriptionManager(androidContext()) }

        // Remote display manager
        single<RemoteDisplayManager> { OnDemandRemoteDisplayManager(androidContext()) }

        // Process-singleton video cache shared by every Media3 player so a
        // recently watched video replays instantly and scrubbing doesn't
        // re-fetch from upstream.
        single { MediaCache(androidContext()) }

        // Pool of warm ExoPlayer instances. Lets video composables hand off
        // players on scroll instead of rebuilding from scratch.
        single { ExoPlayerPool(androidContext(), get()) }
    }
