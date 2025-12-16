package app.logdate.client.sync.cloud

import app.logdate.client.repository.journals.JournalNote
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Tests for DefaultCloudContentDataSource.
 */
class DefaultCloudContentDataSourceTest {
    
    private val mockApiClient = MockCloudApiClientForContent()
    private val dataSource = DefaultCloudContentDataSource(mockApiClient)
    
    @Test
    fun testUploadTextNote() = runTest {
        // Given
        val accessToken = "test-token"
        val textNote = JournalNote.Text(
            uid = Uuid.random(),
            content = "Test content",
            creationTimestamp = Clock.System.now(),
            lastUpdated = Clock.System.now()
        )
        
        val uploadTime = Clock.System.now()
        mockApiClient.uploadContentResponse = Result.success(
            ContentUploadResponse(
                id = textNote.uid.toString(),
                serverVersion = 1,
                uploadedAt = uploadTime.toEpochMilliseconds()
            )
        )
        
        // When
        val result = dataSource.uploadNote(accessToken, textNote)
        
        // Then
        assertTrue(result.isSuccess, "Upload should succeed")
        // Compare with millisecond precision since that's what the API returns
        val expectedTime = Instant.fromEpochMilliseconds(uploadTime.toEpochMilliseconds())
        assertEquals(expectedTime, result.getOrNull(), "Should return upload timestamp")
        assertEquals(1, mockApiClient.uploadCalls.size, "Should make one API call")
        
        val (token, request) = mockApiClient.uploadCalls.first()
        assertEquals(accessToken, token, "Should use correct access token")
        assertEquals(textNote.uid.toString(), request.id, "Should preserve note ID")
        assertEquals("TEXT", request.type, "Should set correct note type")
        assertEquals(textNote.content, request.content, "Should preserve note content")
    }
    
    @Test
    fun testUploadImageNote() = runTest {
        // Given
        val accessToken = "test-token"
        val imageNote = JournalNote.Image(
            uid = Uuid.random(),
            creationTimestamp = Clock.System.now(),
            lastUpdated = Clock.System.now(),
            mediaRef = "file:///test/image.jpg"
        )
        
        val uploadTime = Clock.System.now()
        mockApiClient.uploadContentResponse = Result.success(
            ContentUploadResponse(
                id = imageNote.uid.toString(),
                serverVersion = 1,
                uploadedAt = uploadTime.toEpochMilliseconds()
            )
        )
        
        // When
        val result = dataSource.uploadNote(accessToken, imageNote)
        
        // Then
        assertTrue(result.isSuccess, "Upload should succeed")
        assertEquals(1, mockApiClient.uploadCalls.size, "Should make one API call")
        
        val (_, request) = mockApiClient.uploadCalls.first()
        assertEquals("IMAGE", request.type, "Should set correct note type")
        assertEquals(null, request.content, "Image note should have no text content")
        assertEquals(imageNote.mediaRef, request.mediaUri, "Should preserve media URI")
    }
    
    @Test
    fun testUpdateNote() = runTest {
        // Given
        val accessToken = "test-token"
        val textNote = JournalNote.Text(
            uid = Uuid.random(),
            content = "Updated content",
            creationTimestamp = Clock.System.now(),
            lastUpdated = Clock.System.now()
        )
        
        val updateTime = Clock.System.now()
        mockApiClient.updateContentResponse = Result.success(
            ContentUpdateResponse(
                id = textNote.uid.toString(),
                serverVersion = 2,
                updatedAt = updateTime.toEpochMilliseconds()
            )
        )
        
        // When
        val result = dataSource.updateNote(accessToken, textNote)
        
        // Then
        assertTrue(result.isSuccess, "Update should succeed")
        // Compare with millisecond precision since that's what the API returns
        val expectedTime = Instant.fromEpochMilliseconds(updateTime.toEpochMilliseconds())
        assertEquals(expectedTime, result.getOrNull(), "Should return update timestamp")
        assertEquals(1, mockApiClient.updateCalls.size, "Should make one API call")
        
        val (token, noteId, request) = mockApiClient.updateCalls.first()
        assertEquals(accessToken, token, "Should use correct access token")
        assertEquals(textNote.uid.toString(), noteId, "Should use correct note ID")
        assertEquals(textNote.content, request.content, "Should preserve updated content")
    }
    
    @Test
    fun testDeleteNote() = runTest {
        // Given
        val accessToken = "test-token"
        val noteId = Uuid.random()
        
        mockApiClient.deleteContentResponse = Result.success(Unit)
        
        // When
        val result = dataSource.deleteNote(accessToken, noteId)
        
        // Then
        assertTrue(result.isSuccess, "Delete should succeed")
        assertEquals(1, mockApiClient.deleteCalls.size, "Should make one API call")
        
        val (token, deletedNoteId) = mockApiClient.deleteCalls.first()
        assertEquals(accessToken, token, "Should use correct access token")
        assertEquals(noteId.toString(), deletedNoteId, "Should delete correct note")
    }
    
    @Test
    fun testGetContentChanges() = runTest {
        // Given
        val accessToken = "test-token"
        val since = Clock.System.now()
        val lastTimestamp = Clock.System.now().toEpochMilliseconds()
        
        val noteChange = ContentChange(
            id = Uuid.random().toString(),
            type = "TEXT",
            content = "Remote content",
            mediaUri = null,
            createdAt = Clock.System.now().toEpochMilliseconds(),
            lastUpdated = Clock.System.now().toEpochMilliseconds(),
            serverVersion = 1,
            isDeleted = false
        )
        
        val noteDeletion = ContentDeletion(
            id = Uuid.random().toString(),
            deletedAt = Clock.System.now().toEpochMilliseconds()
        )
        
        mockApiClient.contentChangesResponse = Result.success(
            ContentChangesResponse(
                changes = listOf(noteChange),
                deletions = listOf(noteDeletion),
                lastTimestamp = lastTimestamp
            )
        )
        
        // When
        val result = dataSource.getContentChanges(accessToken, since)
        
        // Then
        assertTrue(result.isSuccess, "Get changes should succeed")
        assertEquals(1, mockApiClient.getChangesCalls.size, "Should make one API call")
        
        val (token, timestamp) = mockApiClient.getChangesCalls.first()
        assertEquals(accessToken, token, "Should use correct access token")
        assertEquals(since.toEpochMilliseconds(), timestamp, "Should pass correct timestamp")
        
        val syncResult = result.getOrNull()!!
        assertEquals(1, syncResult.changes.size, "Should have one change")
        assertEquals(1, syncResult.deletions.size, "Should have one deletion")
        assertEquals(Instant.fromEpochMilliseconds(lastTimestamp), syncResult.lastSyncTimestamp, "Should preserve last sync timestamp")
        
        val convertedNote = syncResult.changes.first()
        assertTrue(convertedNote is JournalNote.Text, "Should convert to correct note type")
        assertEquals(noteChange.content, (convertedNote as JournalNote.Text).content, "Should preserve content")
    }
    
    @Test
    fun testUploadFailure() = runTest {
        // Given
        val accessToken = "test-token"
        val note = JournalNote.Text(
            uid = Uuid.random(),
            content = "Test",
            creationTimestamp = Clock.System.now(),
            lastUpdated = Clock.System.now()
        )
        
        val exception = CloudApiException("UPLOAD_ERROR", "Upload failed", 500)
        mockApiClient.uploadContentResponse = Result.failure(exception)
        
        // When
        val result = dataSource.uploadNote(accessToken, note)
        
        // Then
        assertFalse(result.isSuccess, "Upload should fail")
        assertEquals(exception, result.exceptionOrNull(), "Should preserve exception")
    }
    
    @Test
    fun testGetContentChangesFailure() = runTest {
        // Given
        val accessToken = "test-token"
        val exception = CloudApiException("SERVER_ERROR", "Server error", 500)
        mockApiClient.contentChangesResponse = Result.failure(exception)
        
        // When
        val result = dataSource.getContentChanges(accessToken, Clock.System.now())
        
        // Then
        assertFalse(result.isSuccess, "Get changes should fail")
        assertEquals(exception, result.exceptionOrNull(), "Should preserve exception")
    }
}

/**
 * Mock CloudApiClient for testing content operations.
 */
private class MockCloudApiClientForContent : CloudApiClient {
    var uploadContentResponse: Result<ContentUploadResponse> = Result.success(
        ContentUploadResponse("test-id", 1, Clock.System.now().toEpochMilliseconds())
    )
    
    var updateContentResponse: Result<ContentUpdateResponse> = Result.success(
        ContentUpdateResponse("test-id", 1, Clock.System.now().toEpochMilliseconds())
    )
    
    var deleteContentResponse: Result<Unit> = Result.success(Unit)
    
    var contentChangesResponse: Result<ContentChangesResponse> = Result.success(
        ContentChangesResponse(emptyList(), emptyList(), Clock.System.now().toEpochMilliseconds())
    )
    
    val uploadCalls = mutableListOf<Pair<String, ContentUploadRequest>>()
    val updateCalls = mutableListOf<Triple<String, String, ContentUpdateRequest>>()
    val deleteCalls = mutableListOf<Pair<String, String>>()
    val getChangesCalls = mutableListOf<Pair<String, Long>>()
    
    override suspend fun uploadContent(accessToken: String, content: ContentUploadRequest): Result<ContentUploadResponse> {
        uploadCalls.add(accessToken to content)
        return uploadContentResponse
    }
    
    override suspend fun updateContent(accessToken: String, contentId: String, content: ContentUpdateRequest): Result<ContentUpdateResponse> {
        updateCalls.add(Triple(accessToken, contentId, content))
        return updateContentResponse
    }
    
    override suspend fun deleteContent(accessToken: String, contentId: String): Result<Unit> {
        deleteCalls.add(accessToken to contentId)
        return deleteContentResponse
    }
    
    override suspend fun getContentChanges(accessToken: String, since: Long): Result<ContentChangesResponse> {
        getChangesCalls.add(accessToken to since)
        return contentChangesResponse
    }
    
    // Not needed for content tests - throwing NotImplementedError
    override suspend fun checkUsernameAvailability(username: String): Result<CheckUsernameAvailabilityResponse> = 
        Result.failure(NotImplementedError())
    override suspend fun beginAccountCreation(request: app.logdate.shared.model.BeginAccountCreationRequest): Result<app.logdate.shared.model.BeginAccountCreationResponse> = 
        Result.failure(NotImplementedError())
    override suspend fun completeAccountCreation(request: app.logdate.shared.model.CompleteAccountCreationRequest): Result<app.logdate.shared.model.CompleteAccountCreationResponse> = 
        Result.failure(NotImplementedError())
    override suspend fun refreshAccessToken(refreshToken: String): Result<String> = 
        Result.failure(NotImplementedError())
    override suspend fun getAccountInfo(accessToken: String): Result<AccountInfoResponse> = 
        Result.failure(NotImplementedError())
    override suspend fun uploadJournal(accessToken: String, journal: JournalUploadRequest): Result<JournalUploadResponse> = 
        Result.failure(NotImplementedError())
    override suspend fun getJournalChanges(accessToken: String, since: Long): Result<JournalChangesResponse> = 
        Result.failure(NotImplementedError())
    override suspend fun updateJournal(accessToken: String, journalId: String, journal: JournalUpdateRequest): Result<JournalUpdateResponse> = 
        Result.failure(NotImplementedError())
    override suspend fun deleteJournal(accessToken: String, journalId: String): Result<Unit> = 
        Result.failure(NotImplementedError())
    override suspend fun uploadAssociations(accessToken: String, associations: AssociationUploadRequest): Result<AssociationUploadResponse> = 
        Result.failure(NotImplementedError())
    override suspend fun getAssociationChanges(accessToken: String, since: Long): Result<AssociationChangesResponse> = 
        Result.failure(NotImplementedError())
    override suspend fun deleteAssociations(accessToken: String, associations: AssociationDeleteRequest): Result<Unit> = 
        Result.failure(NotImplementedError())
    override suspend fun uploadMedia(accessToken: String, media: MediaUploadRequest): Result<MediaUploadResponse> = 
        Result.failure(NotImplementedError())
    override suspend fun downloadMedia(accessToken: String, mediaId: String): Result<MediaDownloadResponse> = 
        Result.failure(NotImplementedError())
}