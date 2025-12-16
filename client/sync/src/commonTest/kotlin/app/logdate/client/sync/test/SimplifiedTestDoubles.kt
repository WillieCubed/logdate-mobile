package app.logdate.client.sync.test

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.SyncResult
import app.logdate.client.sync.SyncStatus
import app.logdate.client.sync.SyncError
import app.logdate.client.sync.SyncErrorType
import app.logdate.client.sync.cloud.*
import app.logdate.shared.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.uuid.Uuid

/**
 * Simplified test doubles focused on sync functionality testing.
 * These avoid complex model constructors while providing essential testing capabilities.
 */

/**
 * Simplified mock CloudApiClient for sync testing.
 * Focuses on the sync-related methods rather than account creation complexity.
 */
class SimpleMockCloudApiClient : CloudApiClient {
    
    // Response configurations for sync operations
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
    
    // Call tracking
    val methodCalls = mutableListOf<String>()
    val uploadContentCalls = mutableListOf<Pair<String, ContentUploadRequest>>()
    val updateContentCalls = mutableListOf<Triple<String, String, ContentUpdateRequest>>()
    val deleteContentCalls = mutableListOf<Pair<String, String>>()
    val getContentChangesCalls = mutableListOf<Pair<String, Long>>()
    
    // Account methods - simplified stubs 
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
    
    override suspend fun getAccountInfo(accessToken: String): Result<AccountInfoResponse> {
        methodCalls.add("getAccountInfo")
        return Result.failure(NotImplementedError("Use for sync testing only"))
    }
    
    // Content sync methods - fully implemented for testing
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
    
    override suspend fun getContentChanges(accessToken: String, since: Long): Result<ContentChangesResponse> {
        methodCalls.add("getContentChanges")
        getContentChangesCalls.add(accessToken to since)
        return getContentChangesResponse
    }
    
    // Journal sync methods - fully implemented for testing
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
    
    override suspend fun getJournalChanges(accessToken: String, since: Long): Result<JournalChangesResponse> {
        methodCalls.add("getJournalChanges")
        return getJournalChangesResponse
    }
    
    // Association sync methods - fully implemented for testing
    override suspend fun uploadAssociations(accessToken: String, associations: AssociationUploadRequest): Result<AssociationUploadResponse> {
        methodCalls.add("uploadAssociations")
        return uploadAssociationsResponse
    }
    
    override suspend fun getAssociationChanges(accessToken: String, since: Long): Result<AssociationChangesResponse> {
        methodCalls.add("getAssociationChanges")
        return getAssociationChangesResponse
    }
    
    override suspend fun deleteAssociations(accessToken: String, associations: AssociationDeleteRequest): Result<Unit> {
        methodCalls.add("deleteAssociations")
        return deleteAssociationsResponse
    }
    
    // Media sync methods - fully implemented for testing
    override suspend fun uploadMedia(accessToken: String, media: MediaUploadRequest): Result<MediaUploadResponse> {
        methodCalls.add("uploadMedia")
        return uploadMediaResponse
    }
    
    override suspend fun downloadMedia(accessToken: String, mediaId: String): Result<MediaDownloadResponse> {
        methodCalls.add("downloadMedia")
        return downloadMediaResponse
    }
    
    // Helper methods for testing
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
 * Simplified mock CloudAccountRepository for sync testing.
 * Provides just enough functionality to test authenticated vs unauthenticated states.
 */
class SimpleMockCloudAccountRepository : CloudAccountRepository {
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
    
    // Account management methods - simplified stubs for sync testing
    override suspend fun isUsernameAvailable(username: String): Result<Boolean> = Result.failure(NotImplementedError("Use for sync testing only"))
    override suspend fun beginAccountCreation(username: String, displayName: String, deviceInfo: DeviceInfo?): Result<BeginAccountCreationResult> = Result.failure(NotImplementedError("Use for sync testing only"))
    override suspend fun completeAccountCreation(sessionToken: String, credentialId: String, clientDataJSON: String, attestationObject: String): Result<AuthenticationResult> = Result.failure(NotImplementedError("Use for sync testing only"))
    override suspend fun refreshAccessToken(refreshToken: String): Result<String> = Result.success("new-token")
    override suspend fun signOut(): Result<Boolean> = Result.success(true)
    override suspend fun getPasskeyCredentials(): Result<List<PasskeyCredential>> = Result.success(emptyList())
    override suspend fun associateUserIdentity(userId: Uuid, accountId: String): Result<Boolean> = Result.success(true)
    
    // Helper methods for testing
    fun setAuthenticated(authenticated: Boolean) {
        _isAuthenticated = authenticated
        accessToken = if (authenticated) "test-access-token" else null
        // Update the flow with the appropriate account state
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
 * Mock SyncManager that tracks sync operations for testing.
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
    
    override fun sync(startNow: Boolean) {
        syncCalls++
    }
    
    override suspend fun uploadPendingChanges(): SyncResult {
        uploadPendingChangesCalls++
        return syncResult
    }
    
    override suspend fun downloadRemoteChanges(): SyncResult {
        downloadRemoteChangesCalls++
        return syncResult
    }
    
    override suspend fun syncContent(): SyncResult {
        syncContentCalls++
        return syncResult
    }
    
    override suspend fun syncJournals(): SyncResult {
        syncJournalsCalls++
        return syncResult
    }
    
    override suspend fun syncAssociations(): SyncResult {
        syncAssociationsCalls++
        return syncResult
    }
    
    override suspend fun fullSync(): SyncResult {
        fullSyncCalls++
        return syncResult
    }
    
    override suspend fun getSyncStatus(): SyncStatus {
        getSyncStatusCalls++
        return syncStatus
    }
    
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
 * Simple repository mocks for sync testing.
 */
class SimpleMockJournalNotesRepository : JournalNotesRepository {
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

class SimpleMockJournalRepository : JournalRepository {
    private val journals = mutableListOf<Journal>()
    private val _journalsFlow = MutableStateFlow<List<Journal>>(emptyList())
    
    override val allJournalsObserved: Flow<List<Journal>> = _journalsFlow.asStateFlow()
    
    override fun observeJournalById(id: Uuid): Flow<Journal> {
        TODO("Not implemented in simplified mock")
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
    
    // Draft methods - stubs for testing
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

class SimpleMockJournalContentRepository : JournalContentRepository {
    override fun observeContentForJournal(journalId: Uuid): Flow<List<JournalNote>> = MutableStateFlow(emptyList())
    override fun observeJournalsForContent(contentId: Uuid): Flow<List<Journal>> = MutableStateFlow(emptyList())
    override suspend fun addContentToJournal(contentId: Uuid, journalId: Uuid) {}
    override suspend fun removeContentFromJournal(contentId: Uuid, journalId: Uuid) {}
    override suspend fun addContentToJournals(contentId: Uuid, journalIds: List<Uuid>) {}
    override suspend fun removeContentFromAllJournals(contentId: Uuid) {}
}

/**
 * Factory object for creating common test configurations.
 */
object SimplifiedTestFactory {
    
    fun createAuthenticatedAccountRepository(): SimpleMockCloudAccountRepository {
        return SimpleMockCloudAccountRepository().apply {
            setAuthenticated(true)
        }
    }
    
    fun createUnauthenticatedAccountRepository(): SimpleMockCloudAccountRepository {
        return SimpleMockCloudAccountRepository().apply {
            setAuthenticated(false)
        }
    }
    
    fun createSuccessfulApiClient(): SimpleMockCloudApiClient {
        return SimpleMockCloudApiClient() // All responses are successful by default
    }
    
    fun createFailingApiClient(): SimpleMockCloudApiClient {
        val client = SimpleMockCloudApiClient()
        client.configureContentSyncFailure()
        client.configureJournalSyncFailure()
        return client
    }
    
    fun createRepositoryWithTestData(): SimpleMockJournalNotesRepository {
        val repository = SimpleMockJournalNotesRepository()
        repository.addTestNote("Test note 1")
        repository.addTestNote("Test note 2")
        return repository
    }
}