package app.logdate.di

import androidx.work.WorkManager
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.domain.streak.DEFAULT_STREAK_DAY_START_HOUR
import app.logdate.client.media.storage.MediaFileResolver
import app.logdate.client.sync.datalayer.v2.WearFeatures
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.PeerStateStore
import app.logdate.client.sync.v2.link.PhoneWatchLinkStore
import app.logdate.client.sync.v2.localNodeIdOrNull
import app.logdate.client.sync.v2.media.PhoneMediaEndpoint
import app.logdate.client.sync.v2.media.PhoneMediaServer
import app.logdate.client.sync.v2.receipts.PhoneMomentSupport
import app.logdate.client.sync.v2.window.GoogleWindowDataItems
import app.logdate.client.sync.v2.window.PhoneWindowBodyEndpoint
import app.logdate.client.sync.v2.window.PhoneWindowBodyServer
import app.logdate.client.sync.v2.window.PhoneWindowMoments
import app.logdate.client.sync.v2.window.WatchWindowPublisher
import app.logdate.client.sync.v2.window.WindowDataItems
import app.logdate.client.sync.v2.window.WindowExtras
import app.logdate.client.sync.v2.window.WindowManifestStore
import app.logdate.client.sync.v2.window.WindowResyncResponder
import app.logdate.client.sync.v2.window.WindowRollWork
import app.logdate.client.sync.v2.window.WorkManagerWindowRollWork
import app.logdate.shared.config.LogDateConfigRepository
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.io.File

val phoneWatchWindowModule =
    module {
        single { PhoneMediaServer(get(), get(), get<MediaFileResolver>()::filePath) }
        single { PhoneMediaEndpoint(get(), Wearable.getChannelClient(androidContext())) }
        single { WindowManifestStore(get()) }
        single { PhoneWindowMoments(get(), get()) }
        single {
            val support = get<PhoneMomentSupport>()
            PhoneWindowBodyServer(
                get(),
                get(),
                get(),
                File(androidContext().cacheDir, "watch-window-bodies"),
                moments = get(),
                acceptsMoments = support::accepts,
            )
        }
        single { PhoneWindowBodyEndpoint(get(), Wearable.getChannelClient(androidContext())) }
        single<WindowDataItems> {
            val node = Wearable.getNodeClient(androidContext())
            GoogleWindowDataItems(Wearable.getDataClient(androidContext())) { node.localNodeIdOrNull() }
        }
        single<WindowExtras> { WindowExtras.None }
        single<WindowRollWork> { WorkManagerWindowRollWork(WorkManager.getInstance(androidContext())) }
        single {
            val moments = get<PhoneWindowMoments>()
            val momentSupport = get<PhoneMomentSupport>()
            val preferences = get<LogdatePreferencesDataSource>()
            val states = get<PeerStateStore>()
            val links = get<PhoneWatchLinkStore>()
            val sessions = get<SessionStorage>()
            val config = get<LogDateConfigRepository>()
            WatchWindowPublisher(
                moments = moments.changes,
                acceptsMoments = momentSupport::accepts,
                active = get<FeatureFlagStore>().observe(FeatureFlag.WATCH_EXPERIENCE_V2),
                peers = get(),
                supports = { node ->
                    WearFeatures.WINDOW in
                        states
                            .documents(PeerRole.WATCH)
                            .first()[node]
                            ?.watch
                            ?.features
                            .orEmpty()
                },
                changes =
                    merge(
                        links.observe().map {
                        },
                        states.documents(PeerRole.WATCH).map { },
                        sessions.getSessionFlow().map { },
                        config.backendUrl.map { },
                    ),
                work = get(),
                notes = get(),
                dayStartHour = { preferences.getPreferences().dayStartHour ?: DEFAULT_STREAK_DAY_START_HOUR },
                extras = get(),
                manifest = get(),
                items = get(),
            )
        }
        single { WindowResyncResponder(get(), get()) }
    }
