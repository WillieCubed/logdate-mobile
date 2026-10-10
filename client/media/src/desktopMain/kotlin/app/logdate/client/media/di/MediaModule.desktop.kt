package app.logdate.client.media.di

import app.logdate.client.media.DesktopMediaManager
import app.logdate.client.media.MediaCleaner
import app.logdate.client.media.MediaManager
import app.logdate.client.media.NoOpMediaCleaner
import app.logdate.client.media.audio.transcription.DesktopTranscriptionManager
import app.logdate.client.media.audio.transcription.TranscriptionManager
import app.logdate.client.media.display.RemoteDisplayManager
import app.logdate.client.media.display.UnavailableRemoteDisplayManager
import app.logdate.client.media.storage.DesktopMediaDirectories
import app.logdate.client.media.storage.MediaDirectories
import app.logdate.client.media.storage.MediaFileResolver
import app.logdate.client.media.storage.StoredMediaReferences
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Module that exposes handles for interacting with OS-specific media library APIs.
 */
actual val mediaModule: Module =
    module {
        // Include the audio module only
        includes(audioModule)

        // Media manager
        single<MediaDirectories> { DesktopMediaDirectories() }
        single { MediaFileResolver(get()) }
        single<StoredMediaReferences> { get<MediaFileResolver>() }
        single<MediaManager> { DesktopMediaManager(get()) }
        single<MediaCleaner> { NoOpMediaCleaner }

        // Transcription manager for desktop
        single<TranscriptionManager> {
            DesktopTranscriptionManager(get(), repository = { get() })
        }

        single<RemoteDisplayManager> { UnavailableRemoteDisplayManager() }
    }
