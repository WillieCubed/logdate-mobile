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
import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.media.InMemoryMediaManager
import app.logdate.client.media.MediaPayload
import app.logdate.client.networking.DataRestriction
import app.logdate.client.networking.DataUsageMode
import app.logdate.client.networking.DataUsagePolicy
import app.logdate.client.networking.LocationHistoryApiClientContract
import app.logdate.client.repository.journals.DraftRepository
import app.logdate.client.repository.journals.JournalMergeScope
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.SyncableJournalRepository
import app.logdate.client.sync.DefaultSyncManager
import app.logdate.client.sync.RoomSyncTransactionManager
import app.logdate.client.sync.cloud.AssociationSyncResult
import app.logdate.client.sync.cloud.CloudAssociationDataSource
import app.logdate.client.sync.cloud.CloudRequestLocation
import app.logdate.client.sync.cloud.CloudRequestLocationProvider
import app.logdate.client.sync.cloud.JournalContentAssociation
import app.logdate.client.sync.conflict.JournalConflictResolver
import app.logdate.client.sync.conflict.LastWriteWinsResolver
import app.logdate.client.sync.conflict.SyncConflictRecord
import app.logdate.client.sync.conflict.SyncConflictStore
import app.logdate.client.sync.crypto.AesGcmMediaPayloadCrypto
import app.logdate.client.sync.location.LocationHistorySyncEngine
import app.logdate.client.sync.metadata.DatabaseSyncMetadataService
import app.logdate.client.sync.metadata.MediaSyncRef
import app.logdate.client.sync.metadata.MediaSyncRefStore
import app.logdate.client.sync.metadata.SyncRetryScheduleStore
import app.logdate.client.sync.moments.MomentSyncConfig
import app.logdate.client.sync.recovery.DownloadInbox
import app.logdate.client.sync.recovery.DownloadScope
import app.logdate.client.sync.space.RoomSpaceSwapStore
import app.logdate.client.sync.space.SpaceLocation
import app.logdate.client.sync.space.SpaceMediaDataSource
import app.logdate.client.sync.space.SpaceRecordRegistry
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
import app.logdate.integration.e2e.fixtures.createAccountWithSyntheticPasskey
import app.logdate.integration.e2e.harness.ServerClientE2EHarness
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.model.AccountStorageMode
import app.logdate.shared.model.CloudAccountRepository
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.Journal
import app.logdate.shared.model.sync.LocationHistoryChangesResponse
import app.logdate.shared.model.sync.LocationHistoryRecord
import app.logdate.shared.model.sync.LocationHistoryUpload
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
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.nio.file.Files
import kotlin.random.Random
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal const val JOURNAL = "app.logdate.journal.journal"
internal const val MEMBERSHIP = "app.logdate.journal.membership"
internal const val DRAFT = "app.logdate.journal.draft"
internal const val ENTRY = "app.logdate.journal.entry"
internal const val TRANSCRIPT = "app.logdate.journal.transcript"
internal val IDENTITY_KEY = ByteArray(32) { (it * 5).toByte() }
private val MEDIA_KEY = ByteArray(32) { (it * 11).toByte() }

internal class SpaceAccount(
    val owner: String,
    val did: String,
    val token: String,
    val refreshToken: String,
    val origin: String,
    val apiBaseUrl: String,
)

internal suspend fun ServerClientE2EHarness.signUpForSpace(): SpaceAccount {
    val created = apiClient.createAccountWithSyntheticPasskey("spacesync_${Random.nextInt(100000, 999999)}").data
    return SpaceAccount(
        owner = created.account.id.toString(),
        did = requireNotNull(created.account.did) { "The new account has no DID" },
        token = created.tokens.accessToken,
        refreshToken = created.tokens.refreshToken,
        origin = baseUrl.removeSuffix("/api/v1"),
        apiBaseUrl = baseUrl,
    )
}

/**
 * One device of [account]: its own Room database, outbox, inbox, media store and sync engine,
 * talking to the in-process server over HTTP.
 */
internal class SpaceDevice(
    account: SpaceAccount,
    testScope: CoroutineScope,
) : AutoCloseable {
    /**
     * Everything this device runs in the background, such as watching its queue and its journals. It
     * is stopped before the database closes, so nothing queries a closed database.
     */
    private val background = SupervisorJob(testScope.coroutineContext[Job])
    private val scope = CoroutineScope(testScope.coroutineContext + background)

    private val file = Files.createTempFile("personal-space-device", ".db")
    val database = getRoomDatabase(Room.databaseBuilder<LogDateDatabase>(file.toString()))
    private val config = DefaultLogDateConfigRepository(initialBackendUrl = account.origin)
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
    private val realJournals =
        OfflineFirstJournalRepository(
            journalDao = database.journalDao(),
            remoteDataSource = mockk(relaxed = true),
            draftRepository = drafts,
            syncMetadataService = metadata,
            database = database,
            currentScope = { JournalMergeScope(account.owner, account.origin) },
            mergeTransactionManager = transactions,
            externalScope = scope,
        )

    /** Journals; applying one from the space fails while [failApplying] is set, as a full disk would. */
    val journals = FailingJournals(realJournals)
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
            realJournals,
            database.mediaCaptionDao(),
            transactionManager = transactions,
            syncMetadataService = metadata,
            transcriptionRepository = transcripts,
            syncScope = scope,
        )
    val content =
        OfflineFirstJournalContentRepository(
            database.journalContentDao(),
            realJournals,
            notes,
            metadata,
            transactions,
        )
    val media = FileReadingMediaManager()

    /** This device's id as location history names it. */
    val deviceId: String = Uuid.random().toString()

    /** The location history records, saved places and marked moments this device keeps. */
    val historyStore = RoomHistoryRecordStore(database.historyRecordDao())
    val marks = OfflineFirstMarkedMomentRepository(database.markedMomentDao(), transactions, metadata)
    private val historyScope = HistoryScopeProvider { HistoryScope(account.owner, account.origin) }
    val mediaRefs = InMemoryMediaRefs()
    val conflicts = ListedConflicts()

    /** The sealing key this device holds; another one leaves the space's records unreadable here. */
    var identityKey: ByteArray = IDENTITY_KEY

    /** Answers every personal-space request with `429` and this `Retry-After`, in seconds, while set. */
    var refuseWithRetryAfter: Int? = null

    /** How many personal-space requests reached the server or were refused on the way. */
    var spaceRequests = 0
        private set

    private val http =
        HttpClient(OkHttp) {
            engine {
                addInterceptor(
                    Interceptor { chain ->
                        val request = chain.request()
                        if ("/xrpc/" in request.url.encodedPath) spaceRequests++
                        val wait = refuseWithRetryAfter
                        if (wait == null || "/xrpc/" !in request.url.encodedPath) return@Interceptor chain.proceed(request)
                        Response
                            .Builder()
                            .request(request)
                            .protocol(Protocol.HTTP_1_1)
                            .code(429)
                            .message("Too Many Requests")
                            .header("Retry-After", wait.toString())
                            .body("""{"error":"RateLimitExceeded","message":"Slow down"}""".toResponseBody())
                            .build()
                    },
                )
            }
            install(ContentNegotiation) { json() }
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
    private val homes = XrpcSpaceDataHomes(http)
    private val journalResolver = JournalConflictResolver()
    private val noteResolver = LastWriteWinsResolver<JournalNote>()

    val manager =
        DefaultSyncManager(
            cloudContentDataSource = mockk(relaxed = true),
            cloudJournalDataSource = mockk(relaxed = true),
            cloudAssociationDataSource = NoAssociations,
            cloudMediaDataSource =
                SpaceRoutingMediaDataSource(
                    legacy = mockk(relaxed = true),
                    space =
                        SpaceMediaDataSource(homes, AesGcmMediaPayloadCrypto(MEDIA_KEY), {
                            SpaceLocation(
                                account.origin,
                                app.logdate.space.SpaceRef
                                    .personal(account.did),
                            )
                        }),
                    uploadsToSpace = { true },
                ),
            cloudDraftDataSource = mockk(relaxed = true),
            cloudAccountRepository = accounts,
            sessionStorage = sessions,
            mediaManager = media,
            mediaSyncRefStore = mediaRefs,
            journalRepository = journals,
            journalNotesRepository = notes,
            journalContentRepository = content,
            journalConflictResolver = journalResolver,
            noteConflictResolver = noteResolver,
            conflictStore = conflicts,
            deadLetterStore = mockk(relaxed = true),
            retryScheduleStore = mockk<SyncRetryScheduleStore>(relaxed = true) { coEvery { nextAttemptAt(any(), any()) } returns null },
            syncMetadataService = metadata,
            transactionManager = transactions,
            dataUsagePolicy = UnrestrictedData,
            downloadInbox = inbox,
            syncScope = scope,
            locationHistorySyncEngine =
                LocationHistorySyncEngine(
                    apiFactory = { UnusedHistoryEndpoints },
                    store = historyStore,
                    cipher = mockk(relaxed = true),
                    sessions = sessions,
                    config = config,
                    enabled = { true },
                    savedPlaceIds = { database.userPlaceDao().getAllPlaces().map { it.id } },
                ),
            momentSyncConfig = MomentSyncConfig(mockk(relaxed = true), marks) { false },
            spaceSync =
                SpaceSyncDependencies(
                    registry =
                        SpaceRecordRegistry(
                            listOf(
                                journalSpaceKind(journals, journalResolver),
                                noteSpaceKind(notes, noteResolver),
                                membershipSpaceKind(content),
                                draftSpaceKind(realJournals),
                                markedMomentSpaceKind(marks),
                                placeSpaceKind(historyScope, historyStore, database.userPlaceDao()) { deviceId },
                                placeVisitSpaceKind(historyScope, historyStore) { deviceId },
                                placeCorrectionSpaceKind(historyScope, historyStore) { deviceId },
                                placeEntryLinkSpaceKind(historyScope, historyStore) { deviceId },
                                placeJourneySpaceKind(historyScope, historyStore) { deviceId },
                                locationSamplesSpaceKind(historyScope, historyStore, { account.owner }) { deviceId },
                            ),
                        ),
                    gate = SpaceTransportGate({ true }, { true }, { AccountStorageMode.PERSONAL_SPACE_V1 }),
                    homes = homes,
                    swapStore = RoomSpaceSwapStore(database.spaceRecordStateDao()),
                    accountDid = { account.did },
                    identityKey = { identityKey },
                ),
        )

    override fun close() {
        background.cancel()
        http.close()
        database.close()
        Files.deleteIfExists(file)
        Files.deleteIfExists(file.resolveSibling("${file.fileName}-wal"))
        Files.deleteIfExists(file.resolveSibling("${file.fileName}-shm"))
    }
}

/** The location history endpoints, which an account kept in a personal space never reaches. */
private object UnusedHistoryEndpoints : LocationHistoryApiClientContract {
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

/** Unmetered, unrestricted data, so media syncs. */
private object UnrestrictedData : DataUsagePolicy {
    override val policy = flowOf(DataUsageMode.Unrestricted)

    override suspend fun currentMode() = DataUsageMode.Unrestricted

    override suspend fun currentRestriction() = DataRestriction.NONE
}

/**
 * Memberships stay on their own endpoints, which the server refuses to an account kept in a
 * personal space, and these accounts have none.
 */
private object NoAssociations : CloudAssociationDataSource {
    override suspend fun uploadAssociations(
        accessToken: String,
        associations: List<JournalContentAssociation>,
    ) = Result.success(Instant.fromEpochMilliseconds(0))

    override suspend fun deleteAssociations(
        accessToken: String,
        associations: List<JournalContentAssociation>,
    ) = Result.success(Unit)

    override suspend fun getAssociationChanges(
        accessToken: String,
        since: Instant,
        limit: Int?,
    ) = Result.success(AssociationSyncResult(emptyList(), emptyList(), Instant.fromEpochMilliseconds(0)))
}

/** Journals whose creation from the space fails while [failApplying] is set. */
internal class FailingJournals(
    private val delegate: OfflineFirstJournalRepository,
) : SyncableJournalRepository by delegate {
    var failApplying = false

    override suspend fun createFromSync(journal: Journal) {
        check(!failApplying) { "The disk is full" }
        delegate.createFromSync(journal)
    }
}

/** Which uploaded file each note's media is, kept in memory. */
internal class InMemoryMediaRefs : MediaSyncRefStore {
    private val refs = mutableMapOf<String, MediaSyncRef>()

    override suspend fun get(noteId: Uuid): MediaSyncRef? = refs[noteId.toString()]

    override suspend fun upsert(ref: MediaSyncRef) {
        refs[ref.noteId] = ref
    }

    override suspend fun delete(noteId: Uuid) {
        refs.remove(noteId.toString())
    }
}

/** The conflicts listed for review, kept in memory. */
internal class ListedConflicts : SyncConflictStore {
    val records = mutableListOf<SyncConflictRecord>()

    override suspend fun list(): List<SyncConflictRecord> = records.toList()

    override suspend fun add(record: SyncConflictRecord) {
        records.removeAll { it.id == record.id }
        records += record
    }

    override suspend fun remove(id: String) {
        records.removeAll { it.id == id }
    }

    override suspend fun clear() = records.clear()
}

/** Keeps saved media in memory, and reads a file handed to it from disk as the real stores do. */
internal class FileReadingMediaManager : InMemoryMediaManager() {
    override suspend fun saveMediaFromFile(
        sourceFilePath: String,
        fileName: String,
        mimeType: String,
    ): String {
        val data = SystemFileSystem.source(Path(sourceFilePath)).buffered().use { it.readByteArray() }
        return saveMedia(MediaPayload(fileName, mimeType, data.size.toLong(), data))
    }
}

/** Drafts kept in memory, standing in for the editor's draft storage. */
internal class InMemoryDrafts : DraftRepository {
    private val stored = MutableStateFlow<Map<Uuid, EditorDraft>>(emptyMap())

    override suspend fun saveDraft(draft: EditorDraft) {
        stored.value += draft.id to draft
    }

    override suspend fun getLatestDraft(): EditorDraft? = stored.value.values.maxByOrNull { it.lastModifiedAt }

    override suspend fun getAllDrafts(): List<EditorDraft> = stored.value.values.toList()

    override val allDrafts: Flow<List<EditorDraft>> = stored.map { it.values.toList() }

    override suspend fun getDraft(id: Uuid): EditorDraft? = stored.value[id]

    override suspend fun deleteDraft(id: Uuid) {
        stored.value -= id
    }

    override suspend fun clearAllDrafts() {
        stored.value = emptyMap()
    }
}
