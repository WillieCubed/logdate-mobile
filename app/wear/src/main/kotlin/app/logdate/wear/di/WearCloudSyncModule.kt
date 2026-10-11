package app.logdate.wear.di

import android.content.Context
import app.logdate.client.data.journals.OfflineFirstJournalContentRepository
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.datastore.PrefixedKeyValueStorage
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.device.crypto.ContentEncryptionService
import app.logdate.client.media.AndroidMediaManager
import app.logdate.client.media.MediaManager
import app.logdate.client.networking.DataUsagePolicy
import app.logdate.client.networking.DefaultDataUsagePolicy
import app.logdate.client.networking.httpClient
import app.logdate.client.networking.saver.NetworkSaverModeProvider
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.sensor.battery.AndroidBatteryInfoProvider
import app.logdate.client.sensor.battery.BatteryInfoProvider
import app.logdate.client.sensor.network.AndroidNetworkSaverModeProvider
import app.logdate.client.sync.DefaultSyncManager
import app.logdate.client.sync.RecentCaptureFilter
import app.logdate.client.sync.RoomSyncTransactionManager
import app.logdate.client.sync.SyncProfile
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.client.sync.cloud.CloudApiClient
import app.logdate.client.sync.cloud.DefaultCloudAssociationDataSource
import app.logdate.client.sync.cloud.DefaultCloudContentDataSource
import app.logdate.client.sync.cloud.DefaultCloudDraftDataSource
import app.logdate.client.sync.cloud.DefaultCloudJournalDataSource
import app.logdate.client.sync.cloud.DefaultCloudMediaDataSource
import app.logdate.client.sync.cloud.DeviceSessionRevocationListener
import app.logdate.client.sync.cloud.LogDateCloudApiClient
import app.logdate.client.sync.conflict.KeyValueSyncConflictStore
import app.logdate.client.sync.crypto.MediaPayloadCrypto
import app.logdate.client.sync.crypto.StoredMediaPayloadCrypto
import app.logdate.client.sync.crypto.SyncPayloadCipher
import app.logdate.client.sync.di.SyncQualifiers
import app.logdate.client.sync.metadata.CloudEligibility
import app.logdate.client.sync.metadata.KeyValueFirstSyncEnqueueStore
import app.logdate.client.sync.metadata.KeyValueIdentityRecoveryNeededStore
import app.logdate.client.sync.metadata.KeyValueLastSyncErrorStore
import app.logdate.client.sync.metadata.KeyValueMediaSyncRefStore
import app.logdate.client.sync.metadata.KeyValueSyncDeadLetterStore
import app.logdate.client.sync.metadata.KeyValueSyncRetryScheduleStore
import app.logdate.client.sync.metadata.KeyValueUnreadableCloudRecordStore
import app.logdate.client.sync.metadata.RoutedSyncMetadataService
import app.logdate.client.sync.peer.link.LinkProblem
import app.logdate.client.sync.recovery.DownloadInbox
import app.logdate.client.sync.recovery.DownloadScope
import app.logdate.client.sync.recovery.DurableCloudApiClient
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.CloudAccountRepository
import app.logdate.wear.sync.v2.cloud.WatchCloudAccountRepository
import app.logdate.wear.sync.v2.link.WatchLinkStore
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.module
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/** The qualifier of every binding that belongs to the watch's own cloud engine. */
val WatchCloud = named("watchCloud")

/** Notes the server sends a linked watch are stored only from this far back; the phone's library window adds the rest. */
private val RECENT_CAPTURES = 14.days

/**
 * Koin module for the watch's own cloud engine: the shared engine with the linked-watch profile, its own
 * stores under the `watch_cloud.` prefix, and only the outbox entries its router releases.
 */
val wearCloudSyncModule =
    module {
        single { ContentEncryptionService(get(), get(), get()) }
        single<NetworkSaverModeProvider> { AndroidNetworkSaverModeProvider(get<Context>()) }
        single<DataUsagePolicy> { DefaultDataUsagePolicy(get()) }
        single<BatteryInfoProvider> { AndroidBatteryInfoProvider(get<Context>()) }
        single<MediaManager> { AndroidMediaManager(get<Context>().contentResolver, get<Context>()) }
        single<MediaPayloadCrypto> { StoredMediaPayloadCrypto(get()) }
        single<KeyValueStorage>(WatchCloud) { PrefixedKeyValueStorage(get(), prefix = "watch_cloud.") }
        single<SyncTransactionManager>(WatchCloud) { RoomSyncTransactionManager(get<LogDateDatabase>()) }
        single<CloudApiClient>(WatchCloud) {
            LogDateCloudApiClient(configRepository = get(), httpClient = httpClient, sessionStorage = get())
        }
        single(WatchCloud) {
            val sessions = get<SessionStorage>()
            DownloadInbox(
                get<LogDateDatabase>().downloadInboxDao(),
                get(WatchCloud),
                {
                    val bound = sessions.getOriginBoundSession()
                    DownloadScope(bound?.session?.accountId.orEmpty(), bound?.origin.orEmpty())
                },
                { Clock.System.now().toEpochMilliseconds() },
            )
        }
        single(WatchCloud) { DurableCloudApiClient(get<CloudApiClient>(WatchCloud), get<DownloadInbox>(WatchCloud)) }
        single(WatchCloud) { SyncPayloadCipher(get(), get(), get()) }
        single<CloudAccountRepository>(WatchCloud) {
            WatchCloudAccountRepository(api = get(WatchCloud), config = get())
        }
        single<CloudEligibility>(WatchCloud) { CloudEligibility { _, _ -> false } }
        single<JournalContentRepository>(WatchCloud) {
            OfflineFirstJournalContentRepository(get(), get(), get(), syncMetadataService = get())
        }
        single(WatchCloud) { watchCloudEngine() }
    }

private fun Scope.watchCloudEngine(): DefaultSyncManager {
    val storage = get<KeyValueStorage>(WatchCloud)
    val durable = get<DurableCloudApiClient>(WatchCloud)
    val cipher = get<SyncPayloadCipher>(WatchCloud)
    val config = get<LogDateConfigRepository>()
    val sessions = get<SessionStorage>()
    val links = get<WatchLinkStore>()
    return DefaultSyncManager(
        cloudContentDataSource = DefaultCloudContentDataSource(durable, cipher),
        cloudJournalDataSource = DefaultCloudJournalDataSource(durable, cipher),
        cloudAssociationDataSource = DefaultCloudAssociationDataSource(durable),
        cloudMediaDataSource = DefaultCloudMediaDataSource(get(WatchCloud), get()),
        cloudDraftDataSource = DefaultCloudDraftDataSource(durable, cipher),
        cloudAccountRepository = get(WatchCloud),
        sessionStorage = sessions,
        mediaManager = get(),
        mediaSyncRefStore =
            KeyValueMediaSyncRefStore(
                storage,
                currentOrigin = { config.getCurrentBackendUrl() },
                currentOwnerId = { sessions.getSession()?.accountId.orEmpty() },
            ),
        journalRepository = get(),
        journalNotesRepository = get(),
        journalContentRepository = get(WatchCloud),
        journalConflictResolver = get(named(SyncQualifiers.JOURNAL_CONFLICT_RESOLVER)),
        noteConflictResolver = get(named(SyncQualifiers.NOTE_CONFLICT_RESOLVER)),
        conflictStore = KeyValueSyncConflictStore(storage),
        deadLetterStore = KeyValueSyncDeadLetterStore(storage),
        retryScheduleStore = KeyValueSyncRetryScheduleStore(storage),
        syncMetadataService = RoutedSyncMetadataService(get(), get(WatchCloud)),
        transactionManager = get(WatchCloud),
        dataUsagePolicy = get(),
        deviceIdProvider = get(),
        identityKeyManager = get(),
        mediaPayloadKeyProvider = get(),
        lastErrorStore = KeyValueLastSyncErrorStore(storage),
        firstSyncEnqueueStore = KeyValueFirstSyncEnqueueStore(storage),
        cloudApiClient = get(WatchCloud),
        identityRecoveryNeededStore = KeyValueIdentityRecoveryNeededStore(storage),
        unreadableCloudRecordStore = KeyValueUnreadableCloudRecordStore(storage),
        downloadInbox = get(WatchCloud),
        deviceSessionRevocationListener = DeviceSessionRevocationListener { links.recordProblem(null, LinkProblem.SESSION_REVOKED) },
        profile = SyncProfile.LinkedWatch(RecentCaptureFilter(RECENT_CAPTURES)),
    )
}
