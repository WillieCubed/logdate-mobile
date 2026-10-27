package app.logdate.client.sync.cloud

import app.logdate.shared.model.LogDateAccount
import kotlin.time.Clock

/**
 * Mock CloudApiClient for testing content operations.
 */
internal class MockCloudApiClientForContent : CloudApiClient {
    var uploadContentResponse: Result<ContentUploadResponse> =
        Result.success(
            ContentUploadResponse("test-id", 1, Clock.System.now().toEpochMilliseconds()),
        )

    var updateContentResponse: Result<ContentUpdateResponse> =
        Result.success(
            ContentUpdateResponse("test-id", 1, Clock.System.now().toEpochMilliseconds()),
        )

    var deleteContentResponse: Result<Unit> = Result.success(Unit)

    var contentChangesResponse: Result<ContentChangesResponse> =
        Result.success(
            ContentChangesResponse(emptyList(), emptyList(), Clock.System.now().toEpochMilliseconds()),
        )

    val uploadCalls = mutableListOf<Pair<String, ContentUploadRequest>>()
    val updateCalls = mutableListOf<Triple<String, String, ContentUpdateRequest>>()
    val deleteCalls = mutableListOf<Pair<String, String>>()
    val getChangesCalls = mutableListOf<Triple<String, Long, Int?>>()

    override suspend fun uploadContent(
        accessToken: String,
        content: ContentUploadRequest,
    ): Result<ContentUploadResponse> {
        uploadCalls.add(accessToken to content)
        return uploadContentResponse
    }

    override suspend fun updateContent(
        accessToken: String,
        contentId: String,
        content: ContentUpdateRequest,
    ): Result<ContentUpdateResponse> {
        updateCalls.add(Triple(accessToken, contentId, content))
        return updateContentResponse
    }

    override suspend fun deleteContent(
        accessToken: String,
        contentId: String,
    ): Result<Unit> {
        deleteCalls.add(accessToken to contentId)
        return deleteContentResponse
    }

    override suspend fun getContentChanges(
        accessToken: String,
        since: Long,
        limit: Int?,
    ): Result<ContentChangesResponse> {
        getChangesCalls.add(Triple(accessToken, since, limit))
        return contentChangesResponse
    }

    // Not needed for content tests - throwing NotImplementedError
    override suspend fun checkUsernameAvailability(username: String): Result<CheckUsernameAvailabilityResponse> =
        Result.failure(NotImplementedError())

    override suspend fun beginAccountCreation(
        request: app.logdate.shared.model.BeginAccountCreationRequest,
    ): Result<app.logdate.shared.model.BeginAccountCreationResponse> = Result.failure(NotImplementedError())

    override suspend fun completeAccountCreation(
        request: app.logdate.shared.model.CompleteAccountCreationRequest,
    ): Result<app.logdate.shared.model.CompleteAccountCreationResponse> = Result.failure(NotImplementedError())

    override suspend fun refreshAccessToken(refreshToken: String): Result<String> = Result.failure(NotImplementedError())

    override suspend fun getAccountInfo(accessToken: String): Result<LogDateAccount> = Result.failure(NotImplementedError())

    override suspend fun uploadJournal(
        accessToken: String,
        journal: JournalUploadRequest,
    ): Result<JournalUploadResponse> = Result.failure(NotImplementedError())

    override suspend fun getJournalChanges(
        accessToken: String,
        since: Long,
        limit: Int?,
    ): Result<JournalChangesResponse> = Result.failure(NotImplementedError())

    override suspend fun updateJournal(
        accessToken: String,
        journalId: String,
        journal: JournalUpdateRequest,
    ): Result<JournalUpdateResponse> = Result.failure(NotImplementedError())

    override suspend fun deleteJournal(
        accessToken: String,
        journalId: String,
    ): Result<Unit> = Result.failure(NotImplementedError())

    override suspend fun uploadAssociations(
        accessToken: String,
        associations: AssociationUploadRequest,
    ): Result<AssociationUploadResponse> = Result.failure(NotImplementedError())

    override suspend fun getAssociationChanges(
        accessToken: String,
        since: Long,
        limit: Int?,
    ): Result<AssociationChangesResponse> = Result.failure(NotImplementedError())

    override suspend fun deleteAssociations(
        accessToken: String,
        associations: AssociationDeleteRequest,
    ): Result<Unit> = Result.failure(NotImplementedError())

    override suspend fun uploadDraft(
        accessToken: String,
        draft: DraftUploadRequest,
    ): Result<DraftUploadResponse> = Result.failure(NotImplementedError())

    override suspend fun getDraftChanges(
        accessToken: String,
        since: Long,
        limit: Int?,
    ): Result<DraftChangesResponse> = Result.failure(NotImplementedError())

    override suspend fun deleteDraft(
        accessToken: String,
        draftId: String,
    ): Result<Unit> = Result.failure(NotImplementedError())

    override suspend fun uploadMedia(
        accessToken: String,
        media: MediaUpload,
    ): Result<MediaUploadResponse> = Result.failure(NotImplementedError())

    override suspend fun downloadMedia(
        accessToken: String,
        mediaId: String,
    ): Result<MediaDownloadResponse> = Result.failure(NotImplementedError())
}
