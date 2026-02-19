package app.logdate.client.sync.test

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.media.MediaManager
import app.logdate.client.media.StubMediaManager
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.SyncableJournalNotesRepository
import app.logdate.client.sync.DefaultSyncManager
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.SyncResult
import app.logdate.client.sync.SyncStatus
import app.logdate.client.sync.SyncError
import app.logdate.client.sync.SyncErrorType
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.client.sync.cloud.*
import app.logdate.client.sync.conflict.ConflictResolver
import app.logdate.client.sync.conflict.LastWriteWinsResolver
import app.logdate.client.sync.conflict.SyncConflictRecord
import app.logdate.client.sync.conflict.SyncConflictStore
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.MediaSyncRef
import app.logdate.client.sync.metadata.MediaSyncRefStore
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.PendingUpload
import app.logdate.client.sync.metadata.SyncDeadLetterRecord
import app.logdate.client.sync.metadata.SyncDeadLetterStore
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.metadata.SyncRetryScheduleStore
import app.logdate.shared.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

// =============================================================================
// Top-level factory functions for creating test fakes
// =============================================================================

fun fakeCloudApiClient(): FakeCloudApiClient = FakeCloudApiClient()

fun fakeCloudApiClient(configure: FakeCloudApiClient.() -> Unit): FakeCloudApiClient =
    FakeCloudApiClient().apply(configure)

fun failingCloudApiClient(): FakeCloudApiClient = FakeCloudApiClient().apply {
    configureContentSyncFailure()
    configureJournalSyncFailure()
}

fun fakeAccountRepository(authenticated: Boolean = true): FakeCloudAccountRepository =
    FakeCloudAccountRepository().apply { setAuthenticated(authenticated) }

fun fakeSessionStorage(authenticated: Boolean = true): FakeSessionStorage =
    FakeSessionStorage().apply { if (!authenticated) clearSession() }

fun fakeSyncMetadataService(): FakeSyncMetadataService = FakeSyncMetadataService()

fun fakeJournalNotesRepository(): FakeJournalNotesRepository = FakeJournalNotesRepository()

fun fakeJournalNotesRepository(vararg notes: String): FakeJournalNotesRepository =
    FakeJournalNotesRepository().apply { notes.forEach { addTestNote(it) } }

fun fakeJournalRepository(): FakeJournalRepository = FakeJournalRepository()

fun fakeJournalContentRepository(): FakeJournalContentRepository = FakeJournalContentRepository()

fun trackingSyncManager(): TrackingSyncManager = TrackingSyncManager()

fun <T> lastWriteWinsResolver(): ConflictResolver<T> = LastWriteWinsResolver()

fun testSyncTransactionManager(): TestSyncTransactionManager = TestSyncTransactionManager()

fun testDefaultSyncManager(
    cloudContentDataSource: CloudContentDataSource = DefaultCloudContentDataSource(fakeCloudApiClient()),
    cloudJournalDataSource: CloudJournalDataSource = DefaultCloudJournalDataSource(fakeCloudApiClient()),
    cloudAssociationDataSource: CloudAssociationDataSource = DefaultCloudAssociationDataSource(fakeCloudApiClient()),
    cloudMediaDataSource: CloudMediaDataSource = DefaultCloudMediaDataSource(fakeCloudApiClient()),
    cloudAccountRepository: CloudAccountRepository = fakeAccountRepository(),
    sessionStorage: SessionStorage = fakeSessionStorage(),
    mediaManager: MediaManager = StubMediaManager(),
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
    transactionManager: SyncTransactionManager = testSyncTransactionManager()
): DefaultSyncManager = DefaultSyncManager(
    cloudContentDataSource = cloudContentDataSource,
    cloudJournalDataSource = cloudJournalDataSource,
    cloudAssociationDataSource = cloudAssociationDataSource,
    cloudMediaDataSource = cloudMediaDataSource,
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
    transactionManager = transactionManager
)

// =============================================================================
// Fake implementations
// =============================================================================

/**
 * Fake CloudApiClient for sync testing.
 * All responses are successful by default; configure failures via [configureContentSyncFailure].
 */
open class FakeCloudApiClient : CloudApiClient {

    var uploadContentResponse: Result<ContentUploadResponse> =
        Result.success(ContentUploadResponse("test-id", 1, Clock.System.now().toEpochMilliseconds()))

    var updateContentResponse: Result<ContentUpdateResponse> =
        Result.success(ContentUpdateResponse("test-id", 1, Clock.System.now().toEpochMilliseconds()))

    var deleteContentResponse: Result<Unit> = Result.success(Unit)

    var getContentChangesResponse: Result<ContentChangesResponse> =
        Result.success(ContentChangesResponse(emptyList(), emptyList(), Clock.System.now().toEpochMilliseconds()))

    var uploadJournalResponse: Result<JournalUploadResponse> =
        Result.success(JournalUploadResponse("test-id", 1, Clock.System.now().toEpochMilliseconds()))

    var updateJournalResponse: Result<JournalUpdateResponse> =
        Result.success(JournalUpdateResponse("test-id", 1, Clock.System.now().toEpochMilliseconds()))

    var deleteJournalResponse: Result<Unit> = Result.success(Unit)

    var getJournalChangesResponse: Result<JournalChangesResponse> =
        Result.success(JournalChangesResponse(emptyList(), emptyList(), Clock.System.now().toEpochMilliseconds()))

    var uploadAssociationsResponse: Result<AssociationUploadResponse> =
        Result.success(AssociationUploadResponse(0, Clock.System.now().toEpochMilliseconds()))

    var getAssociationChangesResponse: Result<AssociationChangesResponse> =
        Result.success(AssociationChangesResponse(emptyList(), emptyList(), Clock.System.now().toEpochMilliseconds()))

    var deleteAssociationsResponse: Result<Unit> = Result.success(Unit)

    var uploadMediaResponse: Result<MediaUploadResponse> =
        Result.success(MediaUploadResponse("content-id", "media-id", "https://example.com/media", Clock.System.now().toEpochMilliseconds()))

    var downloadMediaResponse: Result<MediaDownloadResponse> =
        Result.success(MediaDownloadResponse("content-id", "test.jpg", "image/jpeg", 1024, byteArrayOf(), "https://example.com/test.jpg"))

    val methodCalls = mutableListOf<String>()
    val uploadContentCalls = mutableListOf<Pair<String, ContentUploadRequest>>()
    val updateContentCalls = mutableListOf<Triple<String, String, ContentUpdateRequest>>()
    val deleteContentCalls = mutableListOf<Pair<String, String>>()
    val getContentChangesCalls = mutableListOf<Pair<String, Long>>()

    // Account methods - stubs that throw for sync-only testing
    override suspend fun checkUsernameAvailability(username: String): Result<CheckUsernameAvailabilityResponse> {
        methodCalls.add("checkUsernameAvailability")
        return Result.failure(NotImplementedError("Use for sync testing only"))
    }

    override suspend fun beginAccountCreation(request: BeginAccountCreationRequest): Result<BeginAccountCreationResponse> {
        methodCalls.add("beginAccountCreation")
        return Result.failure(NotImplementedError("Use for sync testing only"))
    }

    override suspend fun completeAccountCreation(request: CompleteAccountCreationRequest): Result<CompleteAccountCreationResponse> {
        methodCalls.add("completeAccountCreation")
        return Result.failure(NotImplementedError("Use for sync testing only"))
    }

    override suspend fun refreshAccessToken(refreshToken: String): Result<String> {
        methodCalls.add("refreshAccessToken")
        return Result.success("new-access-token")
    }

    override suspend fun getAccountInfo(accessToken: String): Result<LogDateAccount> {
        methodCalls.add("getAccountInfo")
        return Result.failure(NotImplementedError("Use for sync testing only"))
    }

    // Content sync methods
    override suspend fun uploadContent(accessToken: String, content: ContentUploadRequest): Result<ContentUploadResponse> {
        methodCalls.add("uploadContent")
        uploadContentCalls.add(accessToken to content)
        return uploadContentResponse
    }

    override suspend fun updateContent(accessToken: String, contentId: String, content: ContentUpdateRequest): Result<ContentUpdateResponse> {
        methodCalls.add("updateContent")
        updateContentCalls.add(Triple(accessToken, contentId, content))
        return updateContentResponse
    }

    override suspend fun deleteContent(accessToken: String, contentId: String): Result<Unit> {
        methodCalls.add("deleteContent")
        deleteContentCalls.add(accessToken to contentId)
        return deleteContentResponse
    }

    override suspend fun getContentChanges(accessToken: String, since: Long, limit: Int?): Result<ContentChangesResponse> {
        methodCalls.add("getContentChanges")
        getContentChangesCalls.add(accessToken to since)
        return getContentChangesResponse
    }

    // Journal sync methods
    override suspend fun uploadJournal(accessToken: String, journal: JournalUploadRequest): Result<JournalUploadResponse> {
        methodCalls.add("uploadJournal")
        return uploadJournalResponse
    }

    override suspend fun updateJournal(accessToken: String, journalId: String, journal: JournalUpdateRequest): Result<JournalUpdateResponse> {
        methodCalls.add("updateJournal")
        return updateJournalResponse
    }

    override suspend fun deleteJournal(accessToken: String, journalId: String): Result<Unit> {
        methodCalls.add("deleteJournal")
        return deleteJournalResponse
    }

    override suspend fun getJournalChanges(accessToken: String, since: Long, limit: Int?): Result<JournalChangesResponse> {
        methodCalls.add("getJournalChanges")
        return getJournalChangesResponse
    }

    // Association sync methods
    override suspend fun uploadAssociations(accessToken: String, associations: AssociationUploadRequest): Result<AssociationUploadResponse> {
        methodCalls.add("uploadAssociations")
        return uploadAssociationsResponse
    }

    override suspend fun getAssociationChanges(accessToken: String, since: Long, limit: Int?): Result<AssociationChangesResponse> {
        methodCalls.add("getAssociationChanges")
        return getAssociationChangesResponse
    }

    override suspend fun deleteAssociations(accessToken: String, associations: AssociationDeleteRequest): Result<Unit> {
        methodCalls.add("deleteAssociations")
        return deleteAssociationsResponse
    }

    // Media sync methods
    override suspend fun uploadMedia(accessToken: String, media: MediaUploadRequest): Result<MediaUploadResponse> {
        methodCalls.add("uploadMedia")
        return uploadMediaResponse
    }

    override suspend fun downloadMedia(accessToken: String, mediaId: String): Result<MediaDownloadResponse> {
        methodCalls.add("downloadMedia")
        return downloadMediaResponse
    }

    fun reset() {
        methodCalls.clear()
        uploadContentCalls.clear()
        updateContentCalls.clear()
        deleteContentCalls.clear()
        getContentChangesCalls.clear()
    }

    fun wasMethodCalled(methodName: String): Boolean = methodCalls.contains(methodName)

    fun getMethodCallCount(methodName: String): Int = methodCalls.count { it == methodName }

    fun configureContentSyncFailure(error: Exception = Exception("Content sync failed")) {
        uploadContentResponse = Result.failure(error)
        getContentChangesResponse = Result.failure(error)
        updateContentResponse = Result.failure(error)
        deleteContentResponse = Result.failure(error)
    }

    fun configureJournalSyncFailure(error: Exception = Exception("Journal sync failed")) {
        uploadJournalResponse = Result.failure(error)
        getJournalChangesResponse = Result.failure(error)
        updateJournalResponse = Result.failure(error)
        deleteJournalResponse = Result.failure(error)
    }
}

/**
 * Fake CloudAccountRepository for testing authenticated vs unauthenticated states.
 */
class FakeCloudAccountRepository : CloudAccountRepository {
    private var _isAuthenticated: Boolean = true
    var accessToken: String? = "test-access-token"

    private val _accountFlow = MutableStateFlow<CloudAccount?>(null)

    override suspend fun getCurrentAccount(): CloudAccount? {
        return if (_isAuthenticated) {
            CloudAccount(
                id = Uuid.random(),
                username = "testuser",
                displayName = "Test User",
                userId = Uuid.random(),
                createdAt = Clock.System.now(),
                updatedAt = Clock.System.now(),
                passkeyCredentialIds = emptyList()
            )
        } else null
    }

    override fun observeCurrentAccount(): Flow<CloudAccount?> = _accountFlow.asStateFlow()

    override suspend fun isUsernameAvailable(username: String): Result<Boolean> =
        Result.failure(NotImplementedError("Use for sync testing only"))
    override suspend fun beginAccountCreation(username: String, displayName: String, deviceInfo: DeviceInfo?): Result<BeginAccountCreationResult> =
        Result.failure(NotImplementedError("Use for sync testing only"))
    override suspend fun completeAccountCreation(sessionToken: String, credentialId: String, clientDataJSON: String, attestationObject: String): Result<AuthenticationResult> =
        Result.failure(NotImplementedError("Use for sync testing only"))
    override suspend fun refreshAccessToken(refreshToken: String): Result<String> = Result.success("new-token")
    override suspend fun signOut(): Result<Boolean> = Result.success(true)
    override suspend fun getPasskeyCredentials(): Result<List<PasskeyCredential>> = Result.success(emptyList())
    override suspend fun associateUserIdentity(userId: Uuid, accountId: String): Result<Boolean> = Result.success(true)

    fun setAuthenticated(authenticated: Boolean) {
        _isAuthenticated = authenticated
        accessToken = if (authenticated) "test-access-token" else null
        _accountFlow.value = if (authenticated) {
            CloudAccount(
                id = Uuid.random(),
                username = "testuser",
                displayName = "Test User",
                userId = Uuid.random(),
                createdAt = Clock.System.now(),
                updatedAt = Clock.System.now(),
                passkeyCredentialIds = emptyList()
            )
        } else null
    }
}

/**
 * Fake SessionStorage for testing.
 */
class FakeSessionStorage : SessionStorage {
    private var _session: UserSession? = UserSession(
        accessToken = "test-access-token",
        refreshToken = "test-refresh-token",
        accountId = "test-account-id"
    )
    private val _sessionFlow = MutableStateFlow(_session)

    override fun getSession(): UserSession? = _session
    override fun getSessionFlow(): Flow<UserSession?> = _sessionFlow.asStateFlow()
    override suspend fun hasValidSession(): Boolean = _session != null

    override fun saveSession(session: UserSession) {
        _session = session
        _sessionFlow.value = session
    }

    override fun clearSession() {
        _session = null
        _sessionFlow.value = null
    }
}

/**
 * Fake SyncMetadataService for testing.
 */
class FakeSyncMetadataService : SyncMetadataService {
    private val pendingUploads = mutableMapOf<EntityType, MutableMap<String, PendingOperation>>()
    private val retryCounts = mutableMapOf<EntityType, MutableMap<String, Int>>()
    private val syncTimes = mutableMapOf<EntityType, Instant>()
    private val _pendingCount = MutableStateFlow(0)

    override suspend fun getPendingUploads(entityType: EntityType): List<PendingUpload> =
        pendingUploads[entityType]
            ?.map { (entityId, operation) ->
                val retryCount = retryCounts[entityType]?.get(entityId) ?: 0
                PendingUpload(entityId, operation, retryCount)
            }
            ?: emptyList()

    override suspend fun markAsSynced(entityId: String, entityType: EntityType, syncedAt: Instant, version: Long) {
        pendingUploads[entityType]?.remove(entityId)
        retryCounts[entityType]?.remove(entityId)
        updatePendingCount()
    }

    override suspend fun getLastSyncTime(entityType: EntityType): Instant? = syncTimes[entityType]

    override suspend fun updateLastSyncTime(entityType: EntityType, syncedAt: Instant) {
        updateSyncTime(entityType, syncedAt)
    }

    override suspend fun enqueuePending(entityId: String, entityType: EntityType, operation: PendingOperation) {
        pendingUploads.getOrPut(entityType) { mutableMapOf() }[entityId] = operation
        retryCounts.getOrPut(entityType) { mutableMapOf() }.putIfAbsent(entityId, 0)
        updatePendingCount()
    }

    override suspend fun resetSyncStatus(entityId: String, entityType: EntityType) {
        enqueuePending(entityId, entityType, PendingOperation.UPDATE)
        updatePendingCount()
    }

    override suspend fun getPendingCount(): Int = pendingUploads.values.sumOf { it.size }

    override fun observePendingCount(): Flow<Int> = _pendingCount

    override suspend fun incrementRetryCount(entityId: String, entityType: EntityType) {
        val counts = retryCounts.getOrPut(entityType) { mutableMapOf() }
        counts[entityId] = (counts[entityId] ?: 0) + 1
    }

    private fun updatePendingCount() {
        _pendingCount.value = pendingUploads.values.sumOf { it.size }
    }

    fun addPending(entityId: Uuid, entityType: EntityType, operation: PendingOperation = PendingOperation.UPDATE) {
        pendingUploads.getOrPut(entityType) { mutableMapOf() }[entityId.toString()] = operation
        updatePendingCount()
    }

    fun clear() {
        pendingUploads.clear()
        retryCounts.clear()
        syncTimes.clear()
        _pendingCount.value = 0
    }

    private fun updateSyncTime(entityType: EntityType, syncedAt: Instant) {
        val current = syncTimes[entityType]
        if (current == null || syncedAt >= current) {
            syncTimes[entityType] = syncedAt
        }
    }
}

/**
 * Fake JournalNotesRepository for testing.
 */
class FakeJournalNotesRepository : SyncableJournalNotesRepository {
    private val notes = mutableListOf<JournalNote>()
    private val _notesFlow = MutableStateFlow<List<JournalNote>>(emptyList())

    override val allNotesObserved: Flow<List<JournalNote>> = _notesFlow.asStateFlow()

    override fun observeNotesInJournal(journalId: Uuid): Flow<List<JournalNote>> = _notesFlow.asStateFlow()
    override fun observeNotesInRange(start: Instant, end: Instant): Flow<List<JournalNote>> = _notesFlow.asStateFlow()
    override fun observeNotesPage(pageSize: Int, offset: Int): Flow<List<JournalNote>> = _notesFlow.asStateFlow()
    override fun observeNotesStream(pageSize: Int): Flow<List<JournalNote>> = _notesFlow.asStateFlow()
    override fun observeRecentNotes(limit: Int): Flow<List<JournalNote>> = _notesFlow.asStateFlow()

    override suspend fun create(note: JournalNote): Uuid {
        notes.add(note)
        _notesFlow.value = notes.toList()
        return note.uid
    }

    override suspend fun remove(note: JournalNote) {
        notes.removeAll { it.uid == note.uid }
        _notesFlow.value = notes.toList()
    }

    override suspend fun removeById(noteId: Uuid) {
        notes.removeAll { it.uid == noteId }
        _notesFlow.value = notes.toList()
    }

    override suspend fun create(note: JournalNote, journalId: Uuid) {
        create(note)
    }

    override suspend fun removeFromJournal(noteId: Uuid, journalId: Uuid) {
        // No-op for testing
    }

    override suspend fun getNoteById(noteId: Uuid): JournalNote? =
        notes.find { it.uid == noteId }

    override suspend fun createFromSync(note: JournalNote) {
        create(note)
    }

    override suspend fun deleteFromSync(noteId: Uuid) {
        removeById(noteId)
    }

    override suspend fun updateSyncMetadata(note: JournalNote, syncVersion: Long, syncedAt: Instant) {
        // For testing, just update the note
        val index = notes.indexOfFirst { it.uid == note.uid }
        if (index >= 0) {
            notes[index] = note
            _notesFlow.value = notes.toList()
        }
    }

    override suspend fun updateMediaRef(noteId: Uuid, mediaRef: String) {
        val index = notes.indexOfFirst { it.uid == noteId }
        if (index >= 0) {
            val current = notes[index]
            val updated = when (current) {
                is JournalNote.Image -> current.copy(mediaRef = mediaRef)
                is JournalNote.Video -> current.copy(mediaRef = mediaRef)
                is JournalNote.Audio -> current.copy(mediaRef = mediaRef)
                else -> current
            }
            notes[index] = updated
            _notesFlow.value = notes.toList()
        }
    }

    fun addTestNote(content: String): JournalNote.Text {
        val note = JournalNote.Text(
            uid = Uuid.random(),
            creationTimestamp = Clock.System.now(),
            lastUpdated = Clock.System.now(),
            content = content
        )
        notes.add(note)
        _notesFlow.value = notes.toList()
        return note
    }

    fun clear() {
        notes.clear()
        _notesFlow.value = emptyList()
    }
}

class FailingJournalNotesRepository(
    private val delegate: JournalNotesRepository = FakeJournalNotesRepository()
) : JournalNotesRepository by delegate {
    override suspend fun create(note: JournalNote): Uuid {
        throw IllegalStateException("Simulated note create failure")
    }
}

/**
 * Fake JournalRepository for testing.
 */
class FakeJournalRepository : JournalRepository {
    private val journals = mutableListOf<Journal>()
    private val _journalsFlow = MutableStateFlow<List<Journal>>(emptyList())

    override val allJournalsObserved: Flow<List<Journal>> = _journalsFlow.asStateFlow()

    override fun observeJournalById(id: Uuid): Flow<Journal> {
        throw NotImplementedError("Not implemented in fake")
    }

    override suspend fun getJournalById(id: Uuid): Journal? = journals.find { it.id == id }

    override suspend fun create(journal: Journal): Uuid {
        journals.add(journal)
        _journalsFlow.value = journals.toList()
        return journal.id
    }

    override suspend fun update(journal: Journal) {
        val index = journals.indexOfFirst { it.id == journal.id }
        if (index >= 0) {
            journals[index] = journal
            _journalsFlow.value = journals.toList()
        }
    }

    override suspend fun delete(journalId: Uuid) {
        journals.removeAll { it.id == journalId }
        _journalsFlow.value = journals.toList()
    }

    override suspend fun saveDraft(draft: EditorDraft) {}
    override suspend fun getLatestDraft(): EditorDraft? = null
    override suspend fun getAllDrafts(): List<EditorDraft> = emptyList()
    override suspend fun getDraft(id: Uuid): EditorDraft? = null
    override suspend fun deleteDraft(id: Uuid) {}

    fun clear() {
        journals.clear()
        _journalsFlow.value = emptyList()
    }
}

/**
 * Fake JournalContentRepository for testing.
 */
class FakeJournalContentRepository : JournalContentRepository {
    override fun observeContentForJournal(journalId: Uuid): Flow<List<JournalNote>> = MutableStateFlow(emptyList())
    override fun observeJournalsForContent(contentId: Uuid): Flow<List<Journal>> = MutableStateFlow(emptyList())
    override suspend fun addContentToJournal(contentId: Uuid, journalId: Uuid) {}
    override suspend fun removeContentFromJournal(contentId: Uuid, journalId: Uuid) {}
    override suspend fun addContentToJournals(contentId: Uuid, journalIds: List<Uuid>) {}
    override suspend fun removeContentFromAllJournals(contentId: Uuid) {}
}

class TrackingJournalContentRepository : JournalContentRepository {
    private data class AssociationKey(val contentId: Uuid, val journalId: Uuid)

    private val associations = mutableSetOf<AssociationKey>()

    override fun observeContentForJournal(journalId: Uuid): Flow<List<JournalNote>> =
        MutableStateFlow(emptyList())

    override fun observeJournalsForContent(contentId: Uuid): Flow<List<Journal>> =
        MutableStateFlow(emptyList())

    override suspend fun addContentToJournal(contentId: Uuid, journalId: Uuid) {
        associations.add(AssociationKey(contentId, journalId))
    }

    override suspend fun removeContentFromJournal(contentId: Uuid, journalId: Uuid) {
        associations.remove(AssociationKey(contentId, journalId))
    }

    override suspend fun addContentToJournals(contentId: Uuid, journalIds: List<Uuid>) {
        journalIds.forEach { journalId ->
            addContentToJournal(contentId, journalId)
        }
    }

    override suspend fun removeContentFromAllJournals(contentId: Uuid) {
        associations.removeAll { it.contentId == contentId }
    }

    fun hasAssociation(contentId: Uuid, journalId: Uuid): Boolean {
        return associations.contains(AssociationKey(contentId, journalId))
    }
}

/**
 * SyncManager implementation that tracks all method calls for testing.
 */
class TrackingSyncManager : SyncManager {
    var syncCalls = 0
    var uploadPendingChangesCalls = 0
    var downloadRemoteChangesCalls = 0
    var syncContentCalls = 0
    var syncJournalsCalls = 0
    var syncAssociationsCalls = 0
    var fullSyncCalls = 0
    var getSyncStatusCalls = 0

    var syncResult: SyncResult = SyncResult(success = true)
    var syncStatus: SyncStatus = SyncStatus(
        isEnabled = true,
        lastSyncTime = null,
        pendingUploads = 0,
        isSyncing = false,
        hasErrors = false
    )

    override fun sync(startNow: Boolean) { syncCalls++ }
    override suspend fun uploadPendingChanges(): SyncResult { uploadPendingChangesCalls++; return syncResult }
    override suspend fun downloadRemoteChanges(): SyncResult { downloadRemoteChangesCalls++; return syncResult }
    override suspend fun syncContent(): SyncResult { syncContentCalls++; return syncResult }
    override suspend fun syncJournals(): SyncResult { syncJournalsCalls++; return syncResult }
    override suspend fun syncAssociations(): SyncResult { syncAssociationsCalls++; return syncResult }
    override suspend fun fullSync(): SyncResult { fullSyncCalls++; return syncResult }
    override suspend fun getSyncStatus(): SyncStatus { getSyncStatusCalls++; return syncStatus }

    fun reset() {
        syncCalls = 0
        uploadPendingChangesCalls = 0
        downloadRemoteChangesCalls = 0
        syncContentCalls = 0
        syncJournalsCalls = 0
        syncAssociationsCalls = 0
        fullSyncCalls = 0
        getSyncStatusCalls = 0
    }

    fun configureSyncFailure(errorMessage: String = "Sync failed") {
        syncResult = SyncResult(
            success = false,
            errors = listOf(SyncError(SyncErrorType.NETWORK_ERROR, errorMessage))
        )
    }

    fun configureSyncSuccess(uploadedItems: Int = 0, downloadedItems: Int = 0) {
        syncResult = SyncResult(
            success = true,
            uploadedItems = uploadedItems,
            downloadedItems = downloadedItems,
            lastSyncTime = Clock.System.now()
        )
    }
}

/**
 * Test implementation of SyncTransactionManager that executes blocks directly
 * without actual transaction semantics (suitable for in-memory testing).
 */
class TestSyncTransactionManager : SyncTransactionManager {
    override suspend fun <T> withTransaction(block: suspend () -> T): T {
        return block()
    }
}

class InMemoryMediaSyncRefStore : MediaSyncRefStore {
    private val refs = mutableMapOf<String, MediaSyncRef>()

    override suspend fun get(noteId: Uuid): MediaSyncRef? {
        return refs[noteId.toString()]
    }

    override suspend fun upsert(ref: MediaSyncRef) {
        refs[ref.noteId] = ref
    }

    override suspend fun delete(noteId: Uuid) {
        refs.remove(noteId.toString())
    }
}

class InMemorySyncConflictStore : SyncConflictStore {
    private val conflicts = mutableMapOf<String, SyncConflictRecord>()

    override suspend fun list(): List<SyncConflictRecord> {
        return conflicts.values.toList()
    }

    override suspend fun add(record: SyncConflictRecord) {
        conflicts[record.id] = record
    }

    override suspend fun remove(id: String) {
        conflicts.remove(id)
    }

    override suspend fun clear() {
        conflicts.clear()
    }
}

class InMemorySyncDeadLetterStore : SyncDeadLetterStore {
    private val deadLetters = mutableMapOf<String, SyncDeadLetterRecord>()

    override suspend fun list(): List<SyncDeadLetterRecord> {
        return deadLetters.values.toList()
    }

    override suspend fun add(record: SyncDeadLetterRecord) {
        deadLetters[record.id] = record
    }

    override suspend fun remove(id: String) {
        deadLetters.remove(id)
    }

    override suspend fun clear() {
        deadLetters.clear()
    }
}

class InMemorySyncRetryScheduleStore : SyncRetryScheduleStore {
    private val schedule = mutableMapOf<String, Long>()

    override suspend fun nextAttemptAt(entityType: EntityType, entityId: String): Long? {
        return schedule["${entityType.name}:$entityId"]
    }

    override suspend fun setNextAttemptAt(entityType: EntityType, entityId: String, timestamp: Long) {
        schedule["${entityType.name}:$entityId"] = timestamp
    }

    override suspend fun clear(entityType: EntityType, entityId: String) {
        schedule.remove("${entityType.name}:$entityId")
    }
}
