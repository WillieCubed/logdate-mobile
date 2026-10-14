package app.logdate.di

import androidx.wear.remote.interactions.RemoteActivityHelper
import app.logdate.client.media.audio.AndroidAudioPlaybackManager
import app.logdate.client.media.audio.AudioPlaybackManager
import app.logdate.client.media.audio.AudioPlaybackMetadata
import app.logdate.client.media.audio.AudioPlaybackOptions
import app.logdate.client.media.storage.MediaFileResolver
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.continuity.HandoffFailure
import app.logdate.client.sync.continuity.WatchPlaybackCompanion
import app.logdate.client.sync.continuity.WatchRecordingCompanion
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.v2.continuity.PhonePlaybackCompanion
import app.logdate.client.sync.v2.continuity.PhonePlaybackHandoffHandler
import app.logdate.client.sync.v2.continuity.PhoneRecordingContinuity
import app.logdate.client.sync.v2.receipts.PhoneDeliveryPeers
import app.logdate.client.sync.v2.window.WatchWindowPublisher
import app.logdate.client.watch.watchLinkIntent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.io.File
import java.util.concurrent.TimeUnit

val phoneContinuityModule =
    module {
        single { PhoneRecordingContinuity(get(), Wearable.getMessageClient(androidContext())) }
        single<WatchRecordingCompanion> { get<PhoneRecordingContinuity>() }
        single<WatchPlaybackCompanion> {
            val window = get<WatchWindowPublisher>()
            val remote = RemoteActivityHelper(androidContext())
            PhonePlaybackCompanion(
                get(),
                Wearable.getCapabilityClient(androidContext()),
                Wearable.getMessageClient(androidContext()),
                get<AudioPlaybackManager>() as AndroidAudioPlaybackManager,
                get(),
                publishNote = { node, id ->
                    window.publishIfActive(node)?.let { result ->
                        result.members.any { it.kind == RecordKind.NOTE && it.id == id.toString() }
                    } == true
                },
                openMoment = { node, id ->
                    withContext(Dispatchers.IO) {
                        remote
                            .startRemoteActivity(watchLinkIntent("studio.hypertext.logdate://watch/moment/$id"), node)
                            .get(10, TimeUnit.SECONDS)
                        true
                    }
                },
            )
        }
        single {
            val peers = get<PhoneDeliveryPeers>()
            val notes = get<JournalNotesRepository>()
            val files = get<MediaFileResolver>()
            val playback = get<AudioPlaybackManager>() as AndroidAudioPlaybackManager
            PhonePlaybackHandoffHandler(
                accepts = { peers.current(it) != null },
                audience = { peers.current(it)?.toString() },
                stopRejectedPlayback = { withContext(Dispatchers.Main.immediate) { playback.stopPlayback() } },
                play = { id, position, speed ->
                    val note = notes.getNoteById(id) as? JournalNote.Audio
                    val path = note?.let { files.filePath(it.mediaRef) }
                    if (path == null || !File(path).isFile) {
                        HandoffFailure.AUDIO_NOT_ON_PHONE
                    } else {
                        withContext(Dispatchers.Main.immediate) {
                            playback.pausePlayback()
                            playback.startPlayback(
                                path,
                                AudioPlaybackMetadata(noteId = id),
                                AudioPlaybackOptions(startPositionMs = position, speed = speed),
                                {},
                                {},
                            )
                            val status =
                                withTimeoutOrNull(4_000) {
                                    playback.playbackStatus.first { it.metadata?.noteId == id && (it.isPlaying || it.errorMessage != null) }
                                }
                            if (status?.isPlaying == true) {
                                null
                            } else {
                                playback.stopPlayback()
                                HandoffFailure.PLAYBACK_FAILED
                            }
                        }
                    }
                },
            )
        }
    }
