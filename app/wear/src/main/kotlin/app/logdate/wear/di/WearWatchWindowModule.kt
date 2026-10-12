package app.logdate.wear.di

import android.content.Context
import app.logdate.client.data.transcription.OfflineFirstTranscriptionRepository
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.media.audio.transcription.AndroidTranscriptionManager
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.SyncableJournalNotesRepository
import app.logdate.client.repository.transcription.TranscriptionRepository
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.PeerStateStore
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.wear.sync.SyncedAudioCache
import app.logdate.wear.sync.v2.delivery.LinkedDataLayerTransportV2
import app.logdate.wear.sync.v2.link.WatchLinkStore
import app.logdate.wear.sync.v2.window.DataClientWindowItemSource
import app.logdate.wear.sync.v2.window.WatchTranscriptRepository
import app.logdate.wear.sync.v2.window.WindowApplier
import app.logdate.wear.sync.v2.window.WindowDataEvents
import app.logdate.wear.sync.v2.window.WindowItemSource
import app.logdate.wear.sync.v2.window.WindowMembershipStore
import app.logdate.wear.sync.v2.window.WindowPlaces
import app.logdate.wear.sync.v2.window.WindowProtocol
import app.logdate.wear.sync.v2.window.WindowReconciler
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

val wearWatchWindowModule =
    module {
        single<TranscriptionRepository> {
            WatchTranscriptRepository(
                OfflineFirstTranscriptionRepository(
                    transcriptionDao = get(),
                    audioNoteDao = get(),
                    transcriptionManager = AndroidTranscriptionManager(get()),
                    transactionManager = get<SyncTransactionManager>(WatchCloud),
                ),
            )
        }
        single { WindowMembershipStore(get()) }
        single { WindowPlaces(get()) }
        single {
            val flags = get<FeatureFlagStore>()
            WindowProtocol({ flags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2) }, get(), get(), get())
        }
        single<WindowItemSource> { DataClientWindowItemSource(Wearable.getDataClient(get<Context>())) }
        single {
            val cache = get<SyncedAudioCache>()
            WindowApplier(
                peers = get(),
                notes = get<JournalNotesRepository>() as SyncableJournalNotesRepository,
                outbox = get(),
                members = get(),
                transactions = get<SyncTransactionManager>(WatchCloud),
                forgetMedia = { id ->
                    if (cache.cachedFile(id) != null) {
                        cache.delete(id)
                        check(cache.cachedFile(id) == null) { "The downloaded audio could not be removed" }
                    }
                },
                media = get(),
                prepareNote = get<WindowPlaces>()::ensure,
            )
        }
        single {
            val protocol = get<WindowProtocol>()
            val koin = getKoin()
            val links = get<WatchLinkStore>()
            val states = get<PeerStateStore>()
            val flags = get<FeatureFlagStore>()
            val sessions = get<SessionStorage>()
            val config = get<LogDateConfigRepository>()
            val outbox = get<SyncMetadataService>()
            WindowReconciler(
                peers = get(),
                supported = protocol::supports,
                request = { peer, path, bytes -> koin.get<LinkedDataLayerTransportV2> { parametersOf(peer) }.request(path, bytes) },
                source = get(),
                applier = get(),
                changes =
                    merge(
                        links.observe().map {
                        },
                        states.documents(PeerRole.PHONE).map {
                        },
                        flags.observe(FeatureFlag.WATCH_EXPERIENCE_V2).map {
                        },
                        sessions.getSessionFlow().map { },
                        config.backendUrl.map { },
                        outbox.observePendingUploads().map { },
                    ),
            )
        }
        single { WindowDataEvents(get<WindowProtocol>()::active, get(), get()) }
    }
