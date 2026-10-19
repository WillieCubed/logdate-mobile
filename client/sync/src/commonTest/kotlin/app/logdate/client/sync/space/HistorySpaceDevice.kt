package app.logdate.client.sync.space

import app.logdate.client.database.dao.UserPlaceDao
import app.logdate.client.database.entities.UserPlaceEntity
import app.logdate.client.device.crypto.ContentEncryptionService
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.crypto.KeyDerivation
import app.logdate.client.networking.LocationHistoryApiClientContract
import app.logdate.client.repository.location.HistoryRecord
import app.logdate.client.repository.location.HistoryRecordStore
import app.logdate.client.repository.moments.MarkLocation
import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.client.repository.moments.SyncableMarkedMomentRepository
import app.logdate.client.repository.moments.atMillisecondPrecision
import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.cloud.CloudMomentDataSource
import app.logdate.client.sync.cloud.ConditionalCreateResult
import app.logdate.client.sync.cloud.InMemorySecureStorage
import app.logdate.client.sync.cloud.MomentSyncResult
import app.logdate.client.sync.cloud.SyncUploadResult
import app.logdate.client.sync.cloud.TestCryptoManager
import app.logdate.client.sync.conflict.MarkedMomentConflictResolver
import app.logdate.client.sync.crypto.SyncPayloadCipher
import app.logdate.client.sync.location.LocationHistorySyncEngine
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.moments.MomentSyncConfig
import app.logdate.client.sync.recovery.DownloadInbox
import app.logdate.client.sync.recovery.DownloadInboxTest
import app.logdate.client.sync.recovery.DownloadScope
import app.logdate.client.sync.space.mappers.HistoryScope
import app.logdate.client.sync.space.mappers.HistoryScopeProvider
import app.logdate.client.sync.space.mappers.LocationHour
import app.logdate.client.sync.space.mappers.locationSamplesSpaceKind
import app.logdate.client.sync.space.mappers.markedMomentSpaceKind
import app.logdate.client.sync.space.mappers.placeCorrectionSpaceKind
import app.logdate.client.sync.space.mappers.placeEntryLinkSpaceKind
import app.logdate.client.sync.space.mappers.placeJourneySpaceKind
import app.logdate.client.sync.space.mappers.placeSpaceKind
import app.logdate.client.sync.space.mappers.placeVisitSpaceKind
import app.logdate.client.sync.test.FakeSessionStorage
import app.logdate.client.sync.test.FakeSyncMetadataService
import app.logdate.client.sync.test.InMemorySyncDeadLetterStore
import app.logdate.client.sync.test.InMemorySyncRetryScheduleStore
import app.logdate.client.sync.test.testDefaultSyncManager
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.model.AccountStorageMode
import app.logdate.shared.model.location.ActivityObservation
import app.logdate.shared.model.location.HistoryPayload
import app.logdate.shared.model.location.LocationObservation
import app.logdate.shared.model.sync.DeviceId
import app.logdate.shared.model.sync.LocationHistoryChangesResponse
import app.logdate.shared.model.sync.LocationHistoryRecord
import app.logdate.shared.model.sync.LocationHistoryUpload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.Json
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** The account every [HistorySpaceDevice] of a test belongs to. */
internal const val HISTORY_ACCOUNT = "test-account-id"

/**
 * One device syncing location history and marked moments through [home], with everything else in
 * memory: its own history records, saved places, marks, outbox and download inbox.
 */
internal class HistorySpaceDevice(
    val home: FakeDataHome,
    val deviceId: String = Uuid.random().toString(),
) {
    val sessions = FakeSessionStorage()
    val metadata = FakeSyncMetadataService()
    val database = DownloadInboxTest.Database()
    val inbox = DownloadInbox(database, database, { DownloadScope(HISTORY_ACCOUNT, sessions.currentOrigin) }, { 0L })
    val store = SpaceHistoryStore()
    val savedPlaces = InMemoryUserPlaces()
    val marks = SpaceMarks(metadata)
    val swapStore = InMemorySpaceSwapStore()

    /** Whether this device syncs location history and marks through the personal space at all. */
    var spaceEnabled = true

    private val scope = HistoryScopeProvider { HistoryScope(HISTORY_ACCOUNT, sessions.currentOrigin) }

    private val engine =
        run {
            val crypto = TestCryptoManager()
            val identity = IdentityKeyManager(InMemorySecureStorage(), crypto)
            LocationHistorySyncEngine(
                apiFactory = { UnusedHistoryApi },
                store = store,
                cipher = SyncPayloadCipher(ContentEncryptionService(identity, KeyDerivation(crypto), crypto), identity, crypto),
                sessions = sessions,
                config = DefaultLogDateConfigRepository(initialBackendUrl = sessions.currentOrigin),
                enabled = { true },
                savedPlaceIds = { savedPlaces.getAllPlaces().map { it.id } },
            )
        }

    val kinds =
        listOf(
            markedMomentSpaceKind(marks),
            placeSpaceKind(scope, store, savedPlaces) { deviceId },
            placeVisitSpaceKind(scope, store) { deviceId },
            placeCorrectionSpaceKind(scope, store) { deviceId },
            placeEntryLinkSpaceKind(scope, store) { deviceId },
            placeJourneySpaceKind(scope, store) { deviceId },
            locationSamplesSpaceKind(scope, store, { HISTORY_ACCOUNT }) { deviceId },
        )

    val manager =
        testDefaultSyncManager(
            syncMetadataService = metadata,
            sessionStorage = sessions,
            downloadInbox = inbox,
            retryScheduleStore = InMemorySyncRetryScheduleStore(),
            deadLetterStore = InMemorySyncDeadLetterStore(),
            locationHistorySyncEngine = engine,
            momentSyncConfig = MomentSyncConfig(NoMomentEndpoints, marks) { false },
            spaceSync =
                SpaceSyncDependencies(
                    registry = SpaceRecordRegistry(kinds),
                    gate = SpaceTransportGate({ spaceEnabled }, { true }, { AccountStorageMode.PERSONAL_SPACE_V1 }),
                    homes = SpaceDataHomes { _, token -> home.signedIn(token) },
                    swapStore = swapStore,
                    accountDid = { TEST_DID },
                    identityKey = { TEST_IDENTITY_KEY },
                ),
        )

    /** Records [sample] the way the history stager does: one record, waiting to upload. */
    suspend fun record(sample: LocationObservation) {
        store.put(
            HISTORY_ACCOUNT,
            sessions.currentOrigin,
            HistoryRecord(
                "observation:${sample.id}",
                "observation",
                Json.encodeToString(HistoryPayload.serializer(), HistoryPayload.Observation(sample)),
                sample.deviceId,
            ),
        )
    }

    suspend fun record(change: ActivityObservation) {
        store.put(
            HISTORY_ACCOUNT,
            sessions.currentOrigin,
            HistoryRecord(
                app.logdate.client.device.crypto
                    .activityHistoryRecordId(change.id),
                "activity",
                Json.encodeToString(HistoryPayload.serializer(), HistoryPayload.Activity(change)),
                change.deviceId,
            ),
        )
    }

    /** Stores [payload] as a history record under [id], waiting to upload. */
    suspend fun record(
        id: String,
        type: String,
        payload: HistoryPayload,
    ) {
        val existing = store.record(HISTORY_ACCOUNT, sessions.currentOrigin, id)
        store.put(
            HISTORY_ACCOUNT,
            sessions.currentOrigin,
            HistoryRecord(
                id,
                type,
                Json.encodeToString(HistoryPayload.serializer(), payload),
                deviceId,
                deviceVersion = (existing?.deviceVersion ?: 0) + 1,
            ),
        )
    }

    /** Deletes a sample the way the person's delete does: the record stays as a tombstone without its location. */
    suspend fun deleteSample(sampleId: String) {
        val id = "observation:$sampleId"
        val previous = checkNotNull(store.record(HISTORY_ACCOUNT, sessions.currentOrigin, id)) { "No sample $sampleId" }
        store.putBatch(
            HISTORY_ACCOUNT,
            sessions.currentOrigin,
            listOf(
                HistoryRecord(
                    id,
                    "observation",
                    null,
                    previous.deviceId,
                    previous.deviceVersion + 1,
                    previous.serverVersion,
                    deleted = true,
                ),
            ),
        )
    }

    suspend fun sampleRecord(sampleId: String): HistoryRecord? =
        store.record(HISTORY_ACCOUNT, sessions.currentOrigin, "observation:$sampleId")

    /** Every live sample of [device] in the hour of [at], as the device stores them. */
    suspend fun liveSamples(
        device: String,
        at: Instant,
    ): List<LocationObservation> =
        store
            .recordsBetween(
                HISTORY_ACCOUNT,
                sessions.currentOrigin,
                "observation",
                device,
                LocationHour.hourOf(at).toEpochMilliseconds(),
                LocationHour.hourOf(at).toEpochMilliseconds() + 3_600_000,
            ).filter { !it.deleted }
            .map { (Json.decodeFromString(HistoryPayload.serializer(), it.payload!!) as HistoryPayload.Observation).value }
            .sortedBy { it.timestamp }

    suspend fun pending(type: EntityType) = metadata.getPendingUploads(type)
}

/** The location history endpoints, which an account kept in a personal space never reaches. */
private object UnusedHistoryApi : LocationHistoryApiClientContract {
    override suspend fun upload(
        accessToken: String,
        records: List<LocationHistoryUpload>,
    ): List<LocationHistoryRecord> = error("Location history syncs through the personal space")

    override suspend fun changes(
        accessToken: String,
        since: Long,
        limit: Int,
    ): LocationHistoryChangesResponse = error("Location history syncs through the personal space")
}

/** Marked-moment endpoints that this account's data doesn't use: it lives in the personal space. */
internal object NoMomentEndpoints : CloudMomentDataSource {
    override suspend fun createIfAbsent(
        accessToken: String,
        moment: MarkedMoment,
        deviceId: DeviceId,
    ): Result<ConditionalCreateResult> = Result.failure(unused())

    override suspend fun update(
        accessToken: String,
        moment: MarkedMoment,
        deviceId: DeviceId,
    ): Result<SyncUploadResult> = Result.failure(unused())

    override suspend fun get(
        accessToken: String,
        id: Uuid,
    ): Result<MarkedMoment> = Result.failure(unused())

    override suspend fun delete(
        accessToken: String,
        id: Uuid,
    ): Result<Unit> = Result.failure(unused())

    override suspend fun changes(
        accessToken: String,
        since: Instant,
        limit: Int?,
    ): Result<MomentSyncResult> = Result.failure(unused())

    private fun unused() = CloudApiException("UNUSED", "The marked-moment endpoints aren't used in a personal space")
}

/** An in-memory marked-moment store that queues uploads the way the real one does. */
internal class SpaceMarks(
    private val metadata: FakeSyncMetadataService,
) : SyncableMarkedMomentRepository {
    val rows = mutableMapOf<Uuid, MarkedMoment>()
    val deleted = mutableMapOf<Uuid, Instant>()

    override fun observeOpenMarks(): Flow<List<MarkedMoment>> = flowOf(rows.values.filter { it.state == MarkState.OPEN })

    override suspend fun getMark(id: Uuid): MarkedMoment? = rows[id]

    /** Marks a moment on this device, waiting to upload. */
    suspend fun mark(mark: MarkedMoment) {
        rows[mark.id] = mark.atMillisecondPrecision()
        metadata.enqueuePending(mark.id.toString(), EntityType.MOMENT, PendingOperation.CREATE)
    }

    override suspend fun fill(
        id: Uuid,
        caption: String,
        noteIds: Set<Uuid>,
        updatedAt: Instant,
    ): Boolean {
        val mark = rows[id] ?: return false
        rows[id] =
            mark.copy(
                state = MarkState.FILLED,
                caption = caption,
                resolvedNoteIds = mark.resolvedNoteIds + noteIds,
                lastUpdated = updatedAt,
            )
        metadata.enqueuePending(id.toString(), EntityType.MOMENT, PendingOperation.UPDATE)
        return true
    }

    override suspend fun delete(
        id: Uuid,
        deletedAt: Instant,
    ): Boolean {
        rows.remove(id) ?: return false
        deleted[id] = deletedAt
        metadata.enqueuePending(id.toString(), EntityType.MOMENT, PendingOperation.DELETE)
        return true
    }

    override suspend fun applyFromSync(moment: MarkedMoment): MarkedMoment? {
        if (deleted.containsKey(moment.id)) return null
        val merged = rows[moment.id]?.let { MarkedMomentConflictResolver().merge(it, moment) } ?: moment
        rows[moment.id] = merged.atMillisecondPrecision()
        return rows[moment.id]
    }

    override suspend fun applyDeletionFromSync(
        id: Uuid,
        deletedAt: Instant,
        serverVersion: Long,
    ) {
        rows.remove(id)
        deleted[id] = deletedAt
    }

    override suspend fun applyDeletionFromPeer(
        id: Uuid,
        deletedAt: Instant,
    ): Unit = error("Unused")

    override suspend fun dismiss(
        id: Uuid,
        updatedAt: Instant,
    ): Boolean = error("Unused")

    override suspend fun fillLocationIfMissing(
        id: Uuid,
        location: MarkLocation,
        updatedAt: Instant,
    ): Boolean = error("Unused")
}

/**
 * Location history records kept in memory with the rules the stored ones have: a deletion stays
 * (a record never comes back after it), and keeps the time its record had.
 */
internal class SpaceHistoryStore : HistoryRecordStore {
    val items = mutableListOf<HistoryRecord>()
    private val json = Json { ignoreUnknownKeys = true }

    override fun observe(
        ownerId: String,
        origin: String,
    ): Flow<List<HistoryRecord>> = MutableStateFlow(items.toList())

    override suspend fun records(
        ownerId: String,
        origin: String,
    ): List<HistoryRecord> = items.toList()

    override suspend fun record(
        ownerId: String,
        origin: String,
        id: String,
    ): HistoryRecord? = items.firstOrNull { it.id == id }

    override suspend fun put(
        ownerId: String,
        origin: String,
        record: HistoryRecord,
    ) {
        val existing = items.firstOrNull { it.id == record.id }
        if (existing?.deleted == true && !record.deleted) return
        val observedAt = payloadTime(record) ?: record.observedAtMillis ?: if (record.deleted) existing?.observedAtMillis else null
        val stored = record.copy(observedAtMillis = observedAt)
        if (existing == null) items += stored else items[items.indexOf(existing)] = stored
    }

    override suspend fun putBatch(
        ownerId: String,
        origin: String,
        records: List<HistoryRecord>,
    ) = records.forEach { put(ownerId, origin, it) }

    override suspend fun pending(
        ownerId: String,
        origin: String,
        limit: Int,
    ): List<HistoryRecord> = items.filter { it.dirty }.sortedBy { it.id }.take(limit)

    override suspend fun cursor(
        ownerId: String,
        origin: String,
    ): Long = 0

    override suspend fun applyPage(
        ownerId: String,
        origin: String,
        records: List<HistoryRecord>,
        cursor: Long,
    ) = error("Location history from the personal space doesn't use pages")

    override suspend fun acknowledge(
        ownerId: String,
        origin: String,
        id: String,
        deviceVersion: Long,
        serverVersion: Long,
    ) {
        val existing = items.firstOrNull { it.id == id } ?: return
        items[items.indexOf(existing)] =
            existing.copy(
                dirty = if (existing.deviceVersion == deviceVersion && serverVersion >= existing.serverVersion) false else existing.dirty,
                serverVersion = maxOf(existing.serverVersion, serverVersion),
            )
    }

    private fun payloadTime(record: HistoryRecord): Long? =
        record.payload?.let {
            when (val value = runCatching { json.decodeFromString(HistoryPayload.serializer(), it) }.getOrNull()) {
                is HistoryPayload.Observation -> value.value.timestamp.toEpochMilliseconds()
                is HistoryPayload.Activity -> value.value.timestamp.toEpochMilliseconds()
                else -> null
            }
        }
}

/** Saved places kept in memory. */
internal class InMemoryUserPlaces : UserPlaceDao {
    val rows = mutableMapOf<String, UserPlaceEntity>()

    override suspend fun getAllPlaces(): List<UserPlaceEntity> = rows.values.sortedBy { it.name }

    override fun observeAllPlaces(): Flow<List<UserPlaceEntity>> = flowOf(rows.values.sortedBy { it.name })

    override suspend fun getPlaceById(placeId: String): UserPlaceEntity? = rows[placeId]

    override suspend fun getPlacesInBoundingBox(
        minLatitude: Double,
        maxLatitude: Double,
        minLongitude: Double,
        maxLongitude: Double,
    ): List<UserPlaceEntity> = rows.values.filter { it.latitude in minLatitude..maxLatitude && it.longitude in minLongitude..maxLongitude }

    override suspend fun searchPlaces(query: String): List<UserPlaceEntity> = rows.values.filter { query in it.name }

    override suspend fun insertPlace(place: UserPlaceEntity) {
        rows[place.id] = place
    }

    override suspend fun updatePlace(place: UserPlaceEntity) {
        rows[place.id] = place
    }

    override suspend fun deletePlace(place: UserPlaceEntity) {
        rows.remove(place.id)
    }

    override suspend fun deletePlaceById(placeId: String) {
        rows.remove(placeId)
    }
}
