package app.logdate.client.sync.cloud.account

import app.logdate.client.sync.cloud.CheckUsernameAvailabilityResponse
import app.logdate.client.sync.cloud.CloudApiClient
import app.logdate.client.sync.cloud.ContentChangesResponse
import app.logdate.client.sync.cloud.ContentUpdateRequest
import app.logdate.client.sync.cloud.ContentUpdateResponse
import app.logdate.client.sync.cloud.ContentUploadRequest
import app.logdate.client.sync.cloud.ContentUploadResponse
import app.logdate.client.sync.cloud.DraftUploadRequest
import app.logdate.client.sync.cloud.JournalChangesResponse
import app.logdate.client.sync.cloud.JournalUpdateRequest
import app.logdate.client.sync.cloud.JournalUpdateResponse
import app.logdate.client.sync.cloud.JournalUploadRequest
import app.logdate.client.sync.cloud.JournalUploadResponse
import app.logdate.client.sync.cloud.MediaDownloadResponse
import app.logdate.client.sync.cloud.MediaUpload
import app.logdate.client.sync.cloud.MediaUploadResponse
import app.logdate.client.sync.cloud.RefreshedSession
import app.logdate.shared.model.BeginAccountCreationRequest
import app.logdate.shared.model.BeginAccountCreationResponse
import app.logdate.shared.model.CompleteAccountCreationRequest
import app.logdate.shared.model.CompleteAccountCreationResponse

internal class FakeCloudApiClient(
    private val completeAccountCreationResult: CompleteAccountCreationResponse? = null,
    private val refreshSessionResult: Result<RefreshedSession>? = null,
) : CloudApiClient {
    override suspend fun refreshSession(refreshToken: String): Result<RefreshedSession> =
        refreshSessionResult ?: Result.failure(UnsupportedOperationException("No session refresh configured"))

    override suspend fun checkUsernameAvailability(username: String): Result<CheckUsernameAvailabilityResponse> =
        Result.success(CheckUsernameAvailabilityResponse(available = true, username = username))

    override suspend fun beginAccountCreation(request: BeginAccountCreationRequest): Result<BeginAccountCreationResponse> =
        Result.failure(NotImplementedError())

    override suspend fun completeAccountCreation(request: CompleteAccountCreationRequest): Result<CompleteAccountCreationResponse> =
        Result.success(requireNotNull(completeAccountCreationResult))

    override suspend fun refreshAccessToken(refreshToken: String): Result<String> = Result.success("access")

    override suspend fun getAccountInfo(accessToken: String): Result<app.logdate.shared.model.LogDateAccount> =
        Result.failure(NotImplementedError())

    override suspend fun uploadContent(
        accessToken: String,
        content: ContentUploadRequest,
    ): Result<ContentUploadResponse> = Result.failure(NotImplementedError())

    override suspend fun getContentChanges(
        accessToken: String,
        since: Long,
        limit: Int?,
    ): Result<ContentChangesResponse> = Result.failure(NotImplementedError())

    override suspend fun updateContent(
        accessToken: String,
        contentId: String,
        content: ContentUpdateRequest,
    ): Result<ContentUpdateResponse> = Result.failure(NotImplementedError())

    override suspend fun deleteContent(
        accessToken: String,
        contentId: String,
    ): Result<Unit> = Result.failure(NotImplementedError())

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
        associations: app.logdate.client.sync.cloud.AssociationUploadRequest,
    ): Result<app.logdate.client.sync.cloud.AssociationUploadResponse> = Result.failure(NotImplementedError())

    override suspend fun getAssociationChanges(
        accessToken: String,
        since: Long,
        limit: Int?,
    ): Result<app.logdate.client.sync.cloud.AssociationChangesResponse> = Result.failure(NotImplementedError())

    override suspend fun deleteAssociations(
        accessToken: String,
        associations: app.logdate.client.sync.cloud.AssociationDeleteRequest,
    ): Result<Unit> = Result.failure(NotImplementedError())

    override suspend fun uploadMedia(
        accessToken: String,
        media: MediaUpload,
    ): Result<MediaUploadResponse> = Result.failure(NotImplementedError())

    override suspend fun uploadDraft(
        accessToken: String,
        draft: DraftUploadRequest,
    ) = TODO()

    override suspend fun getDraftChanges(
        accessToken: String,
        since: Long,
        limit: Int?,
    ) = TODO()

    override suspend fun deleteDraft(
        accessToken: String,
        draftId: String,
    ) = TODO()

    override suspend fun downloadMedia(
        accessToken: String,
        mediaId: String,
    ): Result<MediaDownloadResponse> = Result.failure(NotImplementedError())
}
