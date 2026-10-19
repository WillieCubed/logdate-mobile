package app.logdate.client.sync.test

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.media.InMemoryMediaManager
import app.logdate.client.media.MediaManager
import app.logdate.client.networking.DataRestriction
import app.logdate.client.networking.DataUsageMode
import app.logdate.client.networking.DataUsagePolicy
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.sync.CreateCollisionPolicy
import app.logdate.client.sync.DefaultSyncManager
import app.logdate.client.sync.SyncMediaTransfer
import app.logdate.client.sync.SyncProfile
import app.logdate.client.sync.SyncResult
import app.logdate.client.sync.SyncRetryCoordinator
import app.logdate.client.sync.SyncTokenRefresher
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.client.sync.SyncUploader
import app.logdate.client.sync.cloud.CloudApiClient
import app.logdate.client.sync.cloud.CloudAssociationDataSource
import app.logdate.client.sync.cloud.CloudContentDataSource
import app.logdate.client.sync.cloud.CloudDraftDataSource
import app.logdate.client.sync.cloud.CloudJournalDataSource
import app.logdate.client.sync.cloud.CloudMediaDataSource
import app.logdate.client.sync.cloud.DefaultCloudAssociationDataSource
import app.logdate.client.sync.cloud.DefaultCloudContentDataSource
import app.logdate.client.sync.cloud.DefaultCloudDraftDataSource
import app.logdate.client.sync.cloud.DefaultCloudJournalDataSource
import app.logdate.client.sync.cloud.DefaultCloudMediaDataSource
import app.logdate.client.sync.cloud.DeviceSessionRevocationListener
import app.logdate.client.sync.conflict.ConflictResolver
import app.logdate.client.sync.conflict.LastWriteWinsResolver
import app.logdate.client.sync.conflict.SyncConflictStore
import app.logdate.client.sync.crypto.MediaPayloadKeyProvider
import app.logdate.client.sync.location.LocationHistorySyncEngine
import app.logdate.client.sync.metadata.FirstSyncEnqueueStore
import app.logdate.client.sync.metadata.IdentityRecoveryNeededStore
import app.logdate.client.sync.metadata.InMemoryFirstSyncEnqueueStore
import app.logdate.client.sync.metadata.InMemoryIdentityRecoveryNeededStore
import app.logdate.client.sync.metadata.InMemoryLastSyncErrorStore
import app.logdate.client.sync.metadata.InMemoryUnreadableCloudRecordStore
import app.logdate.client.sync.metadata.LastSyncErrorStore
import app.logdate.client.sync.metadata.MediaSyncRefStore
import app.logdate.client.sync.metadata.SyncBackoff
import app.logdate.client.sync.metadata.SyncDeadLetterStore
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.metadata.SyncRetryScheduleStore
import app.logdate.client.sync.metadata.UnreadableCloudRecordStore
import app.logdate.shared.model.CloudAccountRepository
import app.logdate.shared.model.CloudQuotaManager
import app.logdate.shared.model.Journal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

// =============================================================================
// Top-level factory functions for creating test fakes
// =============================================================================

fun fakeCloudApiClient(): FakeCloudApiClient = FakeCloudApiClient()

fun fakeCloudApiClient(configure: FakeCloudApiClient.() -> Unit): FakeCloudApiClient = FakeCloudApiClient().apply(configure)

fun failingCloudApiClient(): FakeCloudApiClient =
    FakeCloudApiClient().apply {
        configureContentSyncFailure()
        configureJournalSyncFailure()
    }

fun fakeAccountRepository(authenticated: Boolean = true): FakeCloudAccountRepository =
    FakeCloudAccountRepository().apply { setAuthenticated(authenticated) }

fun fakeSessionStorage(authenticated: Boolean = true): FakeSessionStorage = FakeSessionStorage(authenticated)

fun fakeSyncMetadataService(): FakeSyncMetadataService = FakeSyncMetadataService()

/** Variant using an explicit session for tests which need to pause and resume sync transport. */
fun fakeSyncMetadataService(sessionStorage: SessionStorage): FakeSyncMetadataService = FakeSyncMetadataService(sessionStorage)

fun fakeFirstSyncEnqueueStore(): InMemoryFirstSyncEnqueueStore = InMemoryFirstSyncEnqueueStore()

/**
 * SessionStorage that always reports an active session. Default for fakes that don't care
 * about auth-gated behavior — keeps the gating logic uniform without nullable plumbing.
 */
internal object AlwaysAuthenticatedSessionStorage : SessionStorage {
    private val flow =
        MutableStateFlow<UserSession?>(
            UserSession(accessToken = "fake", refreshToken = "fake", accountId = "fake"),
        )

    override fun getSession(): UserSession? = flow.value

    override fun getSessionFlow(): Flow<UserSession?> = flow

    override suspend fun hasValidSession(): Boolean = true

    override suspend fun saveSession(session: UserSession) {
        flow.value = session
    }

    override suspend fun clearSession() {
        flow.value = null
    }
}

fun fakeJournalNotesRepository(): FakeJournalNotesRepository = FakeJournalNotesRepository()

fun fakeJournalNotesRepository(vararg notes: String): FakeJournalNotesRepository =
    FakeJournalNotesRepository().apply { notes.forEach { addTestNote(it) } }

fun fakeJournalRepository(): FakeJournalRepository = FakeJournalRepository()

fun fakeJournalContentRepository(): FakeJournalContentRepository = FakeJournalContentRepository()

fun trackingSyncManager(): TrackingSyncManager = TrackingSyncManager()

fun <T> lastWriteWinsResolver(): ConflictResolver<T> = LastWriteWinsResolver()

fun testSyncTransactionManager(): TestSyncTransactionManager = TestSyncTransactionManager()

fun fakeDataUsagePolicy(
    mode: DataUsageMode = DataUsageMode.Unrestricted,
    restriction: DataRestriction = DataRestriction.NONE,
): DataUsagePolicy =
    object : DataUsagePolicy {
        override val policy: Flow<DataUsageMode> = flowOf(mode)

        override suspend fun currentMode(): DataUsageMode = mode

        override suspend fun currentRestriction(): DataRestriction = restriction
    }

fun testDefaultSyncManager(
    cloudContentDataSource: CloudContentDataSource = DefaultCloudContentDataSource(fakeCloudApiClient()),
    cloudJournalDataSource: CloudJournalDataSource = DefaultCloudJournalDataSource(fakeCloudApiClient()),
    cloudAssociationDataSource: CloudAssociationDataSource = DefaultCloudAssociationDataSource(fakeCloudApiClient()),
    cloudMediaDataSource: CloudMediaDataSource = DefaultCloudMediaDataSource(fakeCloudApiClient()),
    cloudDraftDataSource: CloudDraftDataSource = DefaultCloudDraftDataSource(fakeCloudApiClient()),
    cloudAccountRepository: CloudAccountRepository = fakeAccountRepository(),
    sessionStorage: SessionStorage = fakeSessionStorage(),
    mediaManager: MediaManager = InMemoryMediaManager(),
    mediaSyncRefStore: MediaSyncRefStore = InMemoryMediaSyncRefStore(),
    journalRepository: JournalRepository = FakeJournalRepository(),
    journalNotesRepository: JournalNotesRepository = FakeJournalNotesRepository(),
    journalContentRepository: JournalContentRepository = FakeJournalContentRepository(),
    journalConflictResolver: ConflictResolver<Journal> = lastWriteWinsResolver(),
    noteConflictResolver: ConflictResolver<JournalNote> = lastWriteWinsResolver(),
    conflictStore: SyncConflictStore = InMemorySyncConflictStore(),
    deadLetterStore: SyncDeadLetterStore = InMemorySyncDeadLetterStore(),
    retryScheduleStore: SyncRetryScheduleStore = InMemorySyncRetryScheduleStore(),
    syncMetadataService: SyncMetadataService = fakeSyncMetadataService(),
    transactionManager: SyncTransactionManager = testSyncTransactionManager(),
    dataUsagePolicy: DataUsagePolicy = fakeDataUsagePolicy(),
    cloudQuotaManager: CloudQuotaManager? = null,
    syncScope: CoroutineScope? = null,
    lastErrorStore: LastSyncErrorStore = InMemoryLastSyncErrorStore(),
    firstSyncEnqueueStore: FirstSyncEnqueueStore = InMemoryFirstSyncEnqueueStore(),
    identityKeyManager: IdentityKeyManager? = null,
    mediaPayloadKeyProvider: MediaPayloadKeyProvider? = null,
    cloudApiClient: CloudApiClient? = null,
    identityRecoveryNeededStore: IdentityRecoveryNeededStore = InMemoryIdentityRecoveryNeededStore(),
    unreadableCloudRecordStore: UnreadableCloudRecordStore = InMemoryUnreadableCloudRecordStore(),
    locationHistorySyncEngine: LocationHistorySyncEngine? = null,
    downloadInbox: app.logdate.client.sync.recovery.DownloadInbox? = null,
    supportsRichDrafts: () -> Boolean = { false },
    diagnostics: app.logdate.client.sync.diagnostics.SyncDiagnosticRecorder? = null,
    deviceSessionRevocationListener: DeviceSessionRevocationListener = DeviceSessionRevocationListener.None,
    profile: SyncProfile = SyncProfile.Standard,
    createCollisionPolicy: CreateCollisionPolicy = CreateCollisionPolicy.None,
    momentSyncConfig: app.logdate.client.sync.moments.MomentSyncConfig? = null,
    deviceIdProvider: app.logdate.client.device.identity.DeviceIdProvider? = null,
    deviceEnrollment: suspend () -> Unit = {},
    serverAccounts: app.logdate.client.sync.cloud.account.ServerAccountStore? = null,
    spaceSync: app.logdate.client.sync.space.SpaceSyncDependencies? = null,
): DefaultSyncManager =
    if (syncScope == null) {
        DefaultSyncManager(
            cloudContentDataSource = cloudContentDataSource,
            cloudJournalDataSource = cloudJournalDataSource,
            cloudAssociationDataSource = cloudAssociationDataSource,
            cloudMediaDataSource = cloudMediaDataSource,
            cloudDraftDataSource = cloudDraftDataSource,
            cloudAccountRepository = cloudAccountRepository,
            sessionStorage = sessionStorage,
            mediaManager = mediaManager,
            mediaSyncRefStore = mediaSyncRefStore,
            journalRepository = journalRepository,
            journalNotesRepository = journalNotesRepository,
            journalContentRepository = journalContentRepository,
            journalConflictResolver = journalConflictResolver,
            noteConflictResolver = noteConflictResolver,
            conflictStore = conflictStore,
            deadLetterStore = deadLetterStore,
            retryScheduleStore = retryScheduleStore,
            syncMetadataService = syncMetadataService,
            transactionManager = transactionManager,
            dataUsagePolicy = dataUsagePolicy,
            cloudQuotaManager = cloudQuotaManager,
            lastErrorStore = lastErrorStore,
            firstSyncEnqueueStore = firstSyncEnqueueStore,
            identityKeyManager = identityKeyManager,
            mediaPayloadKeyProvider = mediaPayloadKeyProvider,
            cloudApiClient = cloudApiClient,
            identityRecoveryNeededStore = identityRecoveryNeededStore,
            unreadableCloudRecordStore = unreadableCloudRecordStore,
            locationHistorySyncEngine = locationHistorySyncEngine,
            downloadInbox = downloadInbox,
            supportsRichDrafts = supportsRichDrafts,
            diagnostics = diagnostics,
            deviceSessionRevocationListener = deviceSessionRevocationListener,
            profile = profile,
            createCollisionPolicy = createCollisionPolicy,
            momentSyncConfig = momentSyncConfig,
            deviceIdProvider = deviceIdProvider,
            deviceEnrollment = deviceEnrollment,
            serverAccounts = serverAccounts,
            spaceSync = spaceSync,
        )
    } else {
        DefaultSyncManager(
            cloudContentDataSource = cloudContentDataSource,
            cloudJournalDataSource = cloudJournalDataSource,
            cloudAssociationDataSource = cloudAssociationDataSource,
            cloudMediaDataSource = cloudMediaDataSource,
            cloudDraftDataSource = cloudDraftDataSource,
            cloudAccountRepository = cloudAccountRepository,
            sessionStorage = sessionStorage,
            mediaManager = mediaManager,
            mediaSyncRefStore = mediaSyncRefStore,
            journalRepository = journalRepository,
            journalNotesRepository = journalNotesRepository,
            journalContentRepository = journalContentRepository,
            journalConflictResolver = journalConflictResolver,
            noteConflictResolver = noteConflictResolver,
            conflictStore = conflictStore,
            deadLetterStore = deadLetterStore,
            retryScheduleStore = retryScheduleStore,
            syncMetadataService = syncMetadataService,
            transactionManager = transactionManager,
            dataUsagePolicy = dataUsagePolicy,
            cloudQuotaManager = cloudQuotaManager,
            lastErrorStore = lastErrorStore,
            firstSyncEnqueueStore = firstSyncEnqueueStore,
            identityKeyManager = identityKeyManager,
            mediaPayloadKeyProvider = mediaPayloadKeyProvider,
            cloudApiClient = cloudApiClient,
            identityRecoveryNeededStore = identityRecoveryNeededStore,
            unreadableCloudRecordStore = unreadableCloudRecordStore,
            locationHistorySyncEngine = locationHistorySyncEngine,
            downloadInbox = downloadInbox,
            supportsRichDrafts = supportsRichDrafts,
            diagnostics = diagnostics,
            deviceSessionRevocationListener = deviceSessionRevocationListener,
            profile = profile,
            createCollisionPolicy = createCollisionPolicy,
            momentSyncConfig = momentSyncConfig,
            deviceIdProvider = deviceIdProvider,
            deviceEnrollment = deviceEnrollment,
            spaceSync = spaceSync,
            syncScope = syncScope,
            serverAccounts = serverAccounts,
        )
    }

/**
 * Builds a [SyncUploader] directly, with every dependency it doesn't use for
 * [SyncUploader.enqueueEverythingOnFirstSync] left at an inert default -- lets tests of that
 * method alone skip constructing a full [DefaultSyncManager] and its download/upload machinery.
 */
internal fun testSyncUploader(
    journalRepository: JournalRepository = FakeJournalRepository(),
    journalNotesRepository: JournalNotesRepository = FakeJournalNotesRepository(),
    syncMetadataService: SyncMetadataService = fakeSyncMetadataService(),
    firstSyncEnqueueStore: FirstSyncEnqueueStore = InMemoryFirstSyncEnqueueStore(),
    supportsRichDrafts: () -> Boolean = { false },
    draftRepairScope: () -> app.logdate.client.sync.recovery.DownloadScope? = { null },
): SyncUploader {
    val cloudApiClient = fakeCloudApiClient()
    val mediaSyncRefStore: MediaSyncRefStore = InMemoryMediaSyncRefStore()
    val mediaTransfer =
        SyncMediaTransfer(
            mediaManager = InMemoryMediaManager(),
            mediaSyncRefStore = mediaSyncRefStore,
            cloudMediaDataSource = DefaultCloudMediaDataSource(cloudApiClient),
        )
    val retryCoordinator =
        SyncRetryCoordinator(
            retryScheduleStore = InMemorySyncRetryScheduleStore(),
            syncMetadataService = syncMetadataService,
            deadLetterStore = InMemorySyncDeadLetterStore(),
            backoff = SyncBackoff(),
            recordConflict = { _, _, _, _, _, _, _ -> },
        )
    val tokenRefresher = SyncTokenRefresher(fakeSessionStorage(), fakeAccountRepository())

    return SyncUploader(
        transactionManager = testSyncTransactionManager(),
        journalRepository = journalRepository,
        journalNotesRepository = journalNotesRepository,
        cloudJournalDataSource = DefaultCloudJournalDataSource(cloudApiClient),
        cloudContentDataSource = DefaultCloudContentDataSource(cloudApiClient),
        cloudAssociationDataSource = DefaultCloudAssociationDataSource(cloudApiClient),
        cloudDraftDataSource = DefaultCloudDraftDataSource(cloudApiClient),
        mediaSyncRefStore = mediaSyncRefStore,
        syncMetadataService = syncMetadataService,
        dataUsagePolicy = fakeDataUsagePolicy(),
        deviceIdProvider = null,
        tokenRefresher = tokenRefresher,
        mediaTransfer = mediaTransfer,
        retryCoordinator = retryCoordinator,
        firstSyncEnqueueStore = firstSyncEnqueueStore,
        mapCloudApiError = { SyncResult(success = false) },
        mapException = { _, _ -> SyncResult(success = false) },
        recordProgress = {},
        setMediaDeferredForNetwork = {},
        supportsRichDrafts = supportsRichDrafts,
        draftRepairScope = draftRepairScope,
    )
}

// =============================================================================
// Fake implementations
// =============================================================================
