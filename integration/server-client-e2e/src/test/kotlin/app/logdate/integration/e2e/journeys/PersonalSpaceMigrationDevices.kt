@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.integration.e2e.journeys

import androidx.room.Room
import app.logdate.client.data.journals.OfflineFirstJournalContentRepository
import app.logdate.client.data.journals.OfflineFirstJournalRepository
import app.logdate.client.data.location.RoomHistoryRecordStore
import app.logdate.client.data.moments.OfflineFirstMarkedMomentRepository
import app.logdate.client.data.notes.OfflineFirstJournalNotesRepository
import app.logdate.client.data.transcription.OfflineFirstTranscriptionRepository
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.database.getRoomDatabase
import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.crypto.ContentEncryptionService
import app.logdate.client.device.crypto.DesktopCryptoManager
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.crypto.KeyDerivation
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.device.storage.SecureStorage
import app.logdate.client.networking.DataRestriction
import app.logdate.client.networking.DataUsageMode
import app.logdate.client.networking.DataUsagePolicy
import app.logdate.client.networking.LocationHistoryApiClient
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.DefaultSyncManager
import app.logdate.client.sync.RoomSyncTransactionManager
import app.logdate.client.sync.cloud.CloudRequestLocation
import app.logdate.client.sync.cloud.CloudRequestLocationProvider
import app.logdate.client.sync.cloud.DefaultCloudAssociationDataSource
import app.logdate.client.sync.cloud.DefaultCloudContentDataSource
import app.logdate.client.sync.cloud.DefaultCloudDraftDataSource
import app.logdate.client.sync.cloud.DefaultCloudJournalDataSource
import app.logdate.client.sync.cloud.DefaultCloudMediaDataSource
import app.logdate.client.sync.cloud.DefaultCloudMomentDataSource
import app.logdate.client.sync.cloud.LogDateCloudApiClient
import app.logdate.client.sync.cloud.account.ServerAccountStorageMode
import app.logdate.client.sync.cloud.account.ServerAccountStore
import app.logdate.client.sync.conflict.JournalConflictResolver
import app.logdate.client.sync.conflict.LastWriteWinsResolver
import app.logdate.client.sync.crypto.AesGcmMediaPayloadCrypto
import app.logdate.client.sync.crypto.MarkedMomentPayloadCodec
import app.logdate.client.sync.crypto.MediaPayloadCrypto
import app.logdate.client.sync.crypto.NoOpMediaPayloadCrypto
import app.logdate.client.sync.crypto.SyncPayloadCipher
import app.logdate.client.sync.location.LocationHistorySyncEngine
import app.logdate.client.sync.metadata.DatabaseSyncMetadataService
import app.logdate.client.sync.metadata.SyncRetryScheduleStore
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.client.sync.moments.MomentSyncConfig
import app.logdate.client.sync.recovery.DownloadInbox
import app.logdate.client.sync.recovery.DownloadScope
import app.logdate.client.sync.recovery.DurableCloudApiClient
import app.logdate.client.sync.space.RoomSpaceSwapStore
import app.logdate.client.sync.space.SpaceLocation
import app.logdate.client.sync.space.SpaceMediaDataSource
import app.logdate.client.sync.space.SpaceRecordRegistry
import app.logdate.client.sync.space.SpaceRoute
import app.logdate.client.sync.space.SpaceRoutingMediaDataSource
import app.logdate.client.sync.space.SpaceSyncDependencies
import app.logdate.client.sync.space.SpaceTransportGate
import app.logdate.client.sync.space.XrpcSpaceDataHomes
import app.logdate.client.sync.space.mappers.HistoryScope
import app.logdate.client.sync.space.mappers.HistoryScopeProvider
import app.logdate.client.sync.space.mappers.draftSpaceKind
import app.logdate.client.sync.space.mappers.journalSpaceKind
import app.logdate.client.sync.space.mappers.locationSamplesSpaceKind
import app.logdate.client.sync.space.mappers.markedMomentSpaceKind
import app.logdate.client.sync.space.mappers.membershipSpaceKind
import app.logdate.client.sync.space.mappers.noteSpaceKind
import app.logdate.client.sync.space.mappers.placeCorrectionSpaceKind
import app.logdate.client.sync.space.mappers.placeEntryLinkSpaceKind
import app.logdate.client.sync.space.mappers.placeJourneySpaceKind
import app.logdate.client.sync.space.mappers.placeSpaceKind
import app.logdate.client.sync.space.mappers.placeVisitSpaceKind
import app.logdate.client.sync.space.migration.HttpSpaceMigrationApi
import app.logdate.client.sync.space.migration.KeyValueSpaceMigrationCheckpoints
import app.logdate.client.sync.space.migration.SpaceMigrationDependencies
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.model.CloudAccountRepository
import app.logdate.space.SpaceRef
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.nio.file.Files
import kotlin.uuid.Uuid

private val MOVE_MEDIA_KEY = ByteArray(32) { (it * 11).toByte() }

/** Which app a device in the move tests runs. */
internal enum class AppVersion {
    /** Predates client media encryption and personal spaces: the server encrypts its media. */
    BEFORE_MEDIA_ENCRYPTION,

    /** Encrypts media on the device, and syncs only through the per-kind endpoints. */
    LEGACY_ONLY,

    /** This version: syncs a personal space and moves the journal into it. */
    CURRENT,

    /** This version with personal spaces turned off, as a build that predates them reads a moved account. */
    CURRENT_WITHOUT_SPACES,
}

/**
 * One device of [account] running [version]: its own Room database, outbox, inbox, media store,
 * key-value storage and the real sync engine, talking to the in-process server over HTTP through the
 * real legacy data sources and, for [AppVersion.CURRENT], the personal space and the move.
 *
 * Every request goes through [onRequest], which a test can use to refuse or drop requests.
 */
internal class MovingDevice(
    account: SpaceAccount,
    testScope: CoroutineScope,
    val version: AppVersion = AppVersion.CURRENT,
) : AutoCloseable {
    private val background = SupervisorJob(testScope.coroutineContext[Job])
    private val scope = CoroutineScope(testScope.coroutineContext + background)

    private val file = Files.createTempFile("moving-device", ".db")
    val database = getRoomDatabase(Room.databaseBuilder<LogDateDatabase>(file.toString()))
    val config = DefaultLogDateConfigRepository(initialBackendUrl = account.origin)
    private val ownerProvider = mockk<CanonicalOwnerProvider> { coEvery { getCanonicalOwnerId() } returns account.owner }
    val metadata = DatabaseSyncMetadataService(database.syncMetadataDao(), config, ownerProvider)
    private val transactions = RoomSyncTransactionManager(database)
    val inbox =
        DownloadInbox(
            database.downloadInboxDao(),
            transactions,
            { DownloadScope(account.owner, account.origin) },
            { System.currentTimeMillis() },
        )
    val drafts = InMemoryDrafts()
    val journals =
        OfflineFirstJournalRepository(
            journalDao = database.journalDao(),
            remoteDataSource = mockk(relaxed = true),
            draftRepository = drafts,
            syncMetadataService = metadata,
            database = database,
            currentScope = {
                app.logdate.client.repository.journals
                    .JournalMergeScope(account.owner, account.origin)
            },
            mergeTransactionManager = transactions,
            externalScope = scope,
        )
    private val transcripts =
        OfflineFirstTranscriptionRepository(
            database.transcriptionDao(),
            database.audioNoteDao(),
            mockk(relaxed = true),
            transactionManager = transactions,
        )
    val notes =
        OfflineFirstJournalNotesRepository(
            database.textNoteDao(),
            database.imageNoteDao(),
            database.audioNoteDao(),
            database.videoNoteDao(),
            database.journalContentDao(),
            journals,
            database.mediaCaptionDao(),
            transactionManager = transactions,
            syncMetadataService = metadata,
            transcriptionRepository = transcripts,
            syncScope = scope,
        )
    val content = OfflineFirstJournalContentRepository(database.journalContentDao(), journals, notes, metadata, transactions)
    val media = FileReadingMediaManager()
    val mediaRefs = InMemoryMediaRefs()
    val historyStore = RoomHistoryRecordStore(database.historyRecordDao())
    val marks = OfflineFirstMarkedMomentRepository(database.markedMomentDao(), transactions, metadata)
    val deviceId: String = Uuid.random().toString()
    val storage = InMemoryKeyValues()

    /** The status of every request this device sent, by path, for asserting what went over the wire. */
    val requests = mutableListOf<String>()

    /** Decides each request's fate: null sends it, otherwise the response to answer instead. */
    var onRequest: (okhttp3.Request) -> Response? = { null }

    private val http =
        HttpClient(OkHttp) {
            engine {
                addInterceptor(
                    Interceptor { chain ->
                        val request = chain.request()
                        requests += "${request.method} ${request.url.encodedPath}"
                        onRequest(request) ?: chain.proceed(request)
                    },
                )
            }
            install(ContentNegotiation) {
                json(
                    kotlinx.serialization.json.Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    },
                )
            }
        }

    private val session = UserSession(account.token, account.refreshToken, account.owner)
    private val sessions =
        mockk<SessionStorage> {
            every { getSession() } returns session
            every { getSessionFlow() } returns flowOf(session)
            every { getOriginBoundSession() } returns OriginBoundSession(account.origin, session)
        }
    private val accounts =
        object : CloudAccountRepository by mockk(relaxed = true), CloudRequestLocationProvider {
            override fun captureLocation() = CloudRequestLocation(account.origin, account.apiBaseUrl)

            override fun isCurrentOrigin(origin: String) = origin == account.origin
        }
    private val api = LogDateCloudApiClient(configRepository = config, httpClient = http)

    // Record downloads go through the inbox as the app's do, so each one is applied only at the version it staged.
    private val durable = DurableCloudApiClient(api, inbox)
    private val crypto = DesktopCryptoManager()
    val identity = IdentityKeyManager(MovingDeviceSecrets(), crypto)
    private val cipher = SyncPayloadCipher(ContentEncryptionService(identity, KeyDerivation(crypto), crypto), identity, crypto)
    private val mediaCrypto: MediaPayloadCrypto =
        if (version == AppVersion.BEFORE_MEDIA_ENCRYPTION) NoOpMediaPayloadCrypto else AesGcmMediaPayloadCrypto(MOVE_MEDIA_KEY)
    private val homes = XrpcSpaceDataHomes(http)
    private val serverAccounts = ServerAccountStore(storage)
    private val storageMode = ServerAccountStorageMode(sessions, serverAccounts)
    private val gate = SpaceTransportGate({ version == AppVersion.CURRENT }, { true }, storageMode::currentMode)
    private val journalResolver = JournalConflictResolver()
    private val noteResolver = LastWriteWinsResolver<JournalNote>()
    private val historyScope = HistoryScopeProvider { HistoryScope(account.owner, account.origin) }
    val spaceMedia =
        SpaceMediaDataSource(
            homes,
            AesGcmMediaPayloadCrypto(MOVE_MEDIA_KEY),
            { SpaceLocation(account.origin, SpaceRef.personal(account.did)) },
        )
    val checkpoints = KeyValueSpaceMigrationCheckpoints(storage)
    val scopeOfAccount = UploadScope(account.owner, account.origin)

    private val history =
        LocationHistorySyncEngine(
            apiFactory = { LocationHistoryApiClient(http, it) },
            store = historyStore,
            cipher = cipher,
            sessions = sessions,
            config = config,
            enabled = { true },
            savedPlaceIds = { database.userPlaceDao().getAllPlaces().map { it.id } },
        )

    private val spaceSync =
        SpaceSyncDependencies(
            registry =
                SpaceRecordRegistry(
                    listOf(
                        journalSpaceKind(journals, journalResolver),
                        noteSpaceKind(notes, noteResolver),
                        membershipSpaceKind(content),
                        draftSpaceKind(journals),
                        markedMomentSpaceKind(marks),
                        placeSpaceKind(historyScope, historyStore, database.userPlaceDao()) { deviceId },
                        placeVisitSpaceKind(historyScope, historyStore) { deviceId },
                        placeCorrectionSpaceKind(historyScope, historyStore) { deviceId },
                        placeEntryLinkSpaceKind(historyScope, historyStore) { deviceId },
                        placeJourneySpaceKind(historyScope, historyStore) { deviceId },
                        locationSamplesSpaceKind(historyScope, historyStore, { account.owner }) { deviceId },
                    ),
                ),
            gate = gate,
            homes = homes,
            swapStore = RoomSpaceSwapStore(database.spaceRecordStateDao()),
            accountDid = { account.did },
            identityKey = { identity.getIdentityKey() },
        )

    val manager =
        DefaultSyncManager(
            cloudContentDataSource = DefaultCloudContentDataSource(durable, cipher),
            cloudJournalDataSource = DefaultCloudJournalDataSource(durable, cipher),
            cloudAssociationDataSource = DefaultCloudAssociationDataSource(durable),
            cloudMediaDataSource =
                SpaceRoutingMediaDataSource(
                    legacy = DefaultCloudMediaDataSource(api, mediaCrypto),
                    space = spaceMedia,
                    uploadsToSpace = { gate.route() == SpaceRoute.Space },
                ),
            cloudDraftDataSource = DefaultCloudDraftDataSource(durable, cipher) { true },
            cloudAccountRepository = accounts,
            sessionStorage = sessions,
            mediaManager = media,
            mediaSyncRefStore = mediaRefs,
            journalRepository = journals,
            journalNotesRepository = notes,
            journalContentRepository = content,
            journalConflictResolver = journalResolver,
            noteConflictResolver = noteResolver,
            conflictStore = ListedConflicts(),
            deadLetterStore = mockk(relaxed = true),
            retryScheduleStore = mockk<SyncRetryScheduleStore>(relaxed = true) { coEvery { nextAttemptAt(any(), any()) } returns null },
            syncMetadataService = metadata,
            transactionManager = transactions,
            dataUsagePolicy = Unrestricted,
            identityKeyManager = identity,
            deviceIdProvider =
                object : app.logdate.client.device.identity.DeviceIdProvider {
                    private val id = MutableStateFlow(Uuid.parse(deviceId))

                    override fun getDeviceId() = id

                    override suspend fun refreshDeviceId() = Unit
                },
            cloudApiClient = api,
            serverAccounts = serverAccounts,
            downloadInbox = inbox,
            syncScope = scope,
            supportsRichDrafts = { true },
            locationHistorySyncEngine = history,
            momentSyncConfig = MomentSyncConfig(DefaultCloudMomentDataSource(durable, MarkedMomentPayloadCodec(cipher)), marks) { true },
            spaceSync = spaceSync.takeIf { version == AppVersion.CURRENT || version == AppVersion.CURRENT_WITHOUT_SPACES },
            spaceMigration =
                SpaceMigrationDependencies(
                    api = HttpSpaceMigrationApi(http) { account.apiBaseUrl },
                    checkpoints = checkpoints,
                    enabledInThisBuild = { version == AppVersion.CURRENT },
                    serverOffersMove = { true },
                    spaceMedia = spaceMedia,
                    liveMomentIds = { database.markedMomentDao().liveIds().map { it.toString() } },
                    wait = {},
                ).takeIf { version == AppVersion.CURRENT },
            syncRouteStore =
                app.logdate.client.sync
                    .KeyValueSyncRouteStore(storage),
        )

    /** Installs the account's identity key, as a device does once the person signs in or enters their recovery phrase. */
    suspend fun holdIdentityKey(account: SpaceAccount) = identity.installAccountKey(account.owner, IDENTITY_KEY.copyOf())

    override fun close() {
        background.cancel()
        http.close()
        database.close()
        Files.deleteIfExists(file)
        Files.deleteIfExists(file.resolveSibling("${file.fileName}-wal"))
        Files.deleteIfExists(file.resolveSibling("${file.fileName}-shm"))
    }
}

/** A response a test answers instead of sending the request. */
internal fun refuse(
    request: okhttp3.Request,
    code: Int,
    body: String,
): Response =
    Response
        .Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("Refused by the test")
        .body(body.toResponseBody())
        .build()

/** The body of [request] as text, for deciding what to do with it. */
internal fun bodyOf(request: okhttp3.Request): String =
    request.body
        ?.let { body ->
            val buffer = Buffer()
            body.writeTo(buffer)
            buffer.readUtf8()
        }.orEmpty()

private object Unrestricted : DataUsagePolicy {
    override val policy = flowOf(DataUsageMode.Unrestricted)

    override suspend fun currentMode() = DataUsageMode.Unrestricted

    override suspend fun currentRestriction() = DataRestriction.NONE
}

/** Secrets kept in memory, as a device's keystore would keep them. */
private class MovingDeviceSecrets : SecureStorage {
    private val state = MutableStateFlow<Map<String, String>>(emptyMap())

    override suspend fun getString(key: String): String? = state.value[key]

    override suspend fun putString(
        key: String,
        value: String,
    ) {
        state.value += key to value
    }

    override suspend fun remove(key: String) {
        state.value -= key
    }

    override suspend fun clear() {
        state.value = emptyMap()
    }

    override fun observeString(key: String): Flow<String?> = state.map { it[key] }

    override fun observeAll(): Flow<Map<String, String>> = state

    override suspend fun encrypt(data: ByteArray): ByteArray = data

    override suspend fun decrypt(data: ByteArray): ByteArray? = data
}

/** Key-value storage in memory, kept for the life of one device, as the app's own would be across runs. */
internal class InMemoryKeyValues : KeyValueStorage {
    override suspend fun compareAndSetStringsOrThrow(
        expected: Map<String, String?>,
        updates: Map<String, String?>,
    ): Boolean {
        while (true) {
            val before = values.value
            if (!expected.all { (key, value) -> before[key] == value }) return false
            val after =
                before.toMutableMap().apply {
                    updates.forEach { (key, value) ->
                        if (value ==
                            null
                        ) {
                            remove(key)
                        } else {
                            put(key, value)
                        }
                    }
                }
            if (values.compareAndSet(before, after)) return true
        }
    }

    private val values = MutableStateFlow<Map<String, String>>(emptyMap())

    override suspend fun getString(key: String): String? = values.value[key]

    override suspend fun putString(
        key: String,
        value: String,
    ) {
        values.value += key to value
    }

    override suspend fun getBoolean(
        key: String,
        defaultValue: Boolean,
    ): Boolean = values.value[key]?.toBooleanStrictOrNull() ?: defaultValue

    override suspend fun putBoolean(
        key: String,
        value: Boolean,
    ) = putString(key, value.toString())

    override suspend fun getInt(
        key: String,
        defaultValue: Int,
    ): Int = values.value[key]?.toIntOrNull() ?: defaultValue

    override suspend fun putInt(
        key: String,
        value: Int,
    ) = putString(key, value.toString())

    override suspend fun getLong(
        key: String,
        defaultValue: Long,
    ): Long = values.value[key]?.toLongOrNull() ?: defaultValue

    override suspend fun putLong(
        key: String,
        value: Long,
    ) = putString(key, value.toString())

    override suspend fun getFloat(
        key: String,
        defaultValue: Float,
    ): Float = values.value[key]?.toFloatOrNull() ?: defaultValue

    override suspend fun putFloat(
        key: String,
        value: Float,
    ) = putString(key, value.toString())

    override suspend fun remove(key: String) {
        values.value -= key
    }

    override suspend fun contains(key: String): Boolean = key in values.value

    override suspend fun clear() {
        values.value = emptyMap()
    }

    override fun observeString(key: String): Flow<String?> = values.map { it[key] }

    override fun observeBoolean(
        key: String,
        defaultValue: Boolean,
    ): Flow<Boolean> = values.map { it[key]?.toBooleanStrictOrNull() ?: defaultValue }

    override fun observeInt(
        key: String,
        defaultValue: Int,
    ): Flow<Int> = values.map { it[key]?.toIntOrNull() ?: defaultValue }

    override fun observeLong(
        key: String,
        defaultValue: Long,
    ): Flow<Long> = values.map { it[key]?.toLongOrNull() ?: defaultValue }

    override fun observeFloat(
        key: String,
        defaultValue: Float,
    ): Flow<Float> = values.map { it[key]?.toFloatOrNull() ?: defaultValue }
}
