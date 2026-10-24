package app.logdate.client.media.di

import android.content.ContentResolver
import app.logdate.client.media.AndroidMediaCleaner
import app.logdate.client.media.AndroidMediaManager
import app.logdate.client.media.AndroidPhotoLibraryMediaManager
import app.logdate.client.media.MediaCleaner
import app.logdate.client.media.MediaManager
import app.logdate.client.media.audio.transcription.AndroidTranscriptionManager
import app.logdate.client.media.audio.transcription.TranscriptionManager
import app.logdate.client.media.display.OnDemandRemoteDisplayManager
import app.logdate.client.media.display.RemoteDisplayManager
import app.logdate.client.media.storage.AndroidMediaClaimFence
import app.logdate.client.media.storage.AndroidMediaPublicationTickets
import app.logdate.client.media.storage.MediaClaimFence
import app.logdate.client.media.storage.MediaFileResolver
import app.logdate.client.media.storage.MediaReclamationJournal
import app.logdate.client.media.storage.StoredMediaReferences
import app.logdate.client.media.storage.androidMediaFileResolver
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
        single { androidMediaFileResolver(androidContext()) }
        single<StoredMediaReferences> { get<MediaFileResolver>() }
        single<MediaClaimFence> { AndroidMediaClaimFence(androidContext().noBackupFilesDir) }
        single { AndroidMediaPublicationTickets(androidContext().noBackupFilesDir, get()) }
        single<app.logdate.client.media.ManagedMediaPublicationCustody> {
            app.logdate.client.media
                .AndroidManagedMediaPublicationCustody(get())
        }
        // Candidate recording remains gated until every media writer and the collector share a claim fence.
        single<MediaReclamationJournal> { MediaReclamationJournal.None }
        single<MediaManager> {
            AndroidPhotoLibraryMediaManager(
                AndroidMediaManager(
                    get(),
                    get(),
                    canonicalMediaStore =
                        app.logdate.client.media.storage.AndroidCanonicalMediaStore(
                            androidContext().filesDir,
                            publicationTickets = get(),
                        ),
                    mediaFiles = get(),
                ),
                androidContext(),
            )
        }
        single<MediaCleaner> { AndroidMediaCleaner(get()) }
        factory {
            val manager = get<MediaManager>()
            app.logdate.client.media.ManagedMediaImporter(
                manager,
                app.logdate.client.media
                    .AndroidManagedMediaImportSource(androidContext(), stagingParent = androidContext().filesDir),
                app.logdate.client.media
                    .AndroidManagedMediaDiscarder(androidContext(), manager)::discard,
                app.logdate.client.media
                    .AndroidManagedMediaPublicationCustody(get()),
            )
        }

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
