package app.logdate.client.sync.cloud

import app.logdate.shared.model.BeginAccountCreationRequest
import app.logdate.shared.model.BeginAccountCreationResponse
import app.logdate.shared.model.CompleteAccountCreationRequest
import app.logdate.shared.model.CompleteAccountCreationResponse
import app.logdate.shared.model.LogDateAccount

/**
 * Interface defining the API client for LogDate Cloud services.
 *
 * This client handles communication with the LogDate Cloud API for
 * account management, authentication, and data synchronization.
 */
interface CloudApiClient {
    suspend fun createMomentIfAbsent(
        accessToken: String,
        moment: app.logdate.shared.model.sync.MomentUploadRequest,
    ): Result<ConditionalCreateResult> = Result.failure(UnsupportedOperationException("Moments are not supported"))

    suspend fun updateMoment(
        accessToken: String,
        momentId: String,
        moment: app.logdate.shared.model.sync.MomentUpdateRequest,
    ): Result<app.logdate.shared.model.sync.MomentUpdateResponse> =
        Result.failure(UnsupportedOperationException("Moments are not supported"))

    suspend fun getMoment(
        accessToken: String,
        momentId: String,
    ): Result<app.logdate.shared.model.sync.MomentChange> = Result.failure(UnsupportedOperationException("Moments are not supported"))

    suspend fun getMomentChanges(
        accessToken: String,
        since: Long,
        limit: Int? = null,
    ): Result<app.logdate.shared.model.sync.MomentChangesResponse> =
        Result.failure(UnsupportedOperationException("Moments are not supported"))

    suspend fun deleteMoment(
        accessToken: String,
        momentId: String,
    ): Result<Unit> = Result.failure(UnsupportedOperationException("Moments are not supported"))

    suspend fun mergeJournals(
        accessToken: String,
        sourceId: String,
        request: app.logdate.shared.model.sync.JournalMergeRequest,
    ): Result<app.logdate.shared.model.sync.JournalMergeResponse> =
        Result.failure(UnsupportedOperationException("Journal merge is not supported"))

    suspend fun uploadBackupFile(
        accessToken: String,
        backup: BackupUploadFileRequest,
    ): Result<BackupUploadResponse> = Result.failure(UnsupportedOperationException("Streaming backup uploads are not supported"))

    suspend fun downloadBackupToFile(
        accessToken: String,
        backupId: String,
        destination: kotlinx.io.files.Path,
    ): Result<BackupInfoResponse> = Result.failure(UnsupportedOperationException("Streaming backup downloads are not supported"))

    /**
     * Checks if a username is available for registration.
     *
     * @param username The username to check availability for.
     * @return Response indicating if the username is available.
     * @throws CloudApiException If the request fails.
     */
    suspend fun checkUsernameAvailability(username: String): Result<CheckUsernameAvailabilityResponse>

    /**
     * Begins the account creation process.
     *
     * This initiates the passkey registration process and returns
     * the necessary challenge and options for creating a WebAuthn credential.
     *
     * @param request The account creation request containing user details.
     * @return Response with session token and registration options.
     * @throws CloudApiException If the request fails.
     */
    suspend fun beginAccountCreation(request: BeginAccountCreationRequest): Result<BeginAccountCreationResponse>

    /**
     * Completes the account creation process.
     *
     * This submits the passkey credential created by the client to finalize
     * the account creation process.
     *
     * @param request The request containing the passkey credential.
     * @return Response with the created account details and authentication tokens.
     * @throws CloudApiException If the request fails.
     */
    suspend fun completeAccountCreation(request: CompleteAccountCreationRequest): Result<CompleteAccountCreationResponse>

    /**
     * Refreshes an expired access token.
     *
     * @param refreshToken The refresh token to use.
     * @return A new access token if successful.
     * @throws CloudApiException If the request fails.
     */
    suspend fun refreshAccessToken(refreshToken: String): Result<String>

    /**
     * Refreshes the session, returning the refresh token the server rotated to, when it did. A linked
     * device's session slides forward on every refresh, so a rotated refresh token must be saved.
     */
    suspend fun refreshSession(refreshToken: String): Result<RefreshedSession> =
        Result.failure(UnsupportedOperationException("Session refresh is not supported by this cloud client"))

    /**
     * Gets the current account information.
     *
     * @param accessToken The access token for authentication.
     * @return The account information if the request is successful.
     * @throws CloudApiException If the request fails.
     */
    suspend fun getAccountInfo(accessToken: String): Result<LogDateAccount>

    // Content Sync Operations

    /**
     * Uploads new content (notes) to the cloud.
     */
    suspend fun uploadContent(
        accessToken: String,
        content: ContentUploadRequest,
    ): Result<ContentUploadResponse>

    /**
     * Downloads content changes since the specified timestamp.
     */
    suspend fun getContentChanges(
        accessToken: String,
        since: Long,
        limit: Int? = null,
    ): Result<ContentChangesResponse>

    /**
     * Updates existing content.
     */
    suspend fun updateContent(
        accessToken: String,
        contentId: String,
        content: ContentUpdateRequest,
    ): Result<ContentUpdateResponse>

    /**
     * Deletes content by ID.
     */
    suspend fun deleteContent(
        accessToken: String,
        contentId: String,
    ): Result<Unit>

    // Journal Metadata Sync Operations

    /**
     * Uploads journal metadata to the cloud.
     */
    suspend fun uploadJournal(
        accessToken: String,
        journal: JournalUploadRequest,
    ): Result<JournalUploadResponse>

    /**
     * Downloads journal changes since the specified timestamp.
     */
    suspend fun getJournalChanges(
        accessToken: String,
        since: Long,
        limit: Int? = null,
    ): Result<JournalChangesResponse>

    /**
     * Updates existing journal metadata.
     */
    suspend fun updateJournal(
        accessToken: String,
        journalId: String,
        journal: JournalUpdateRequest,
    ): Result<JournalUpdateResponse>

    /**
     * Deletes journal by ID.
     */
    suspend fun deleteJournal(
        accessToken: String,
        journalId: String,
    ): Result<Unit>

    // Association Sync Operations

    /**
     * Uploads journal-content associations to the cloud.
     */
    suspend fun uploadAssociations(
        accessToken: String,
        associations: AssociationUploadRequest,
    ): Result<AssociationUploadResponse>

    /**
     * Downloads association changes since the specified timestamp.
     */
    suspend fun getAssociationChanges(
        accessToken: String,
        since: Long,
        limit: Int? = null,
    ): Result<AssociationChangesResponse>

    /**
     * Deletes specific associations.
     */
    suspend fun deleteAssociations(
        accessToken: String,
        associations: AssociationDeleteRequest,
    ): Result<Unit>

    // Create-if-absent operations, for linked devices. Never sent to a server that does not advertise
    // ServerProtocolFeature.CONDITIONAL_CREATE_V1: an older server ignores the condition and overwrites.

    /** Creates the content only if the server has no record with its ID, answering what it found. */
    suspend fun createContentIfAbsent(
        accessToken: String,
        content: ContentUploadRequest,
    ): Result<ConditionalCreateResult> =
        Result.failure(UnsupportedOperationException("Create-if-absent is not supported by this cloud client"))

    /** Creates the journal only if the server has no record with its ID, answering what it found. */
    suspend fun createJournalIfAbsent(
        accessToken: String,
        journal: JournalUploadRequest,
    ): Result<ConditionalCreateResult> =
        Result.failure(UnsupportedOperationException("Create-if-absent is not supported by this cloud client"))

    /** Reads whether the server holds a content record, without downloading it. */
    suspend fun contentState(
        accessToken: String,
        contentId: String,
    ): Result<ContentState> = Result.failure(UnsupportedOperationException("Content state is not supported by this cloud client"))

    /**
     * Uploads the associations the server has never had. Links it already holds or deleted are named in
     * the response and left alone, so a deleted link is never brought back.
     */
    suspend fun uploadAssociationsIfAbsent(
        accessToken: String,
        associations: AssociationUploadRequest,
    ): Result<AssociationUploadResponse> =
        Result.failure(UnsupportedOperationException("Create-if-absent is not supported by this cloud client"))

    // Draft Operations

    suspend fun uploadDraft(
        accessToken: String,
        draft: DraftUploadRequest,
    ): Result<DraftUploadResponse>

    suspend fun getDraftChanges(
        accessToken: String,
        since: Long,
        limit: Int? = null,
    ): Result<DraftChangesResponse>

    suspend fun deleteDraft(
        accessToken: String,
        draftId: String,
    ): Result<Unit>

    // Media Operations
    suspend fun getMediaUploadReceipt(
        accessToken: String,
        uploadIntentId: String,
    ): Result<MediaUploadResponse?> = Result.failure(UnsupportedOperationException("Media upload receipts are not supported"))

    /**
     * Uploads a media file to the cloud, streaming its body from [MediaUpload.openBody].
     */
    suspend fun uploadMedia(
        accessToken: String,
        media: MediaUpload,
    ): Result<MediaUploadResponse>

    /**
     * Downloads media file by ID.
     */
    suspend fun downloadMedia(
        accessToken: String,
        mediaId: String,
    ): Result<MediaDownloadResponse>

    // Encrypted device backup operations. The server receives the payload over TLS and
    // encrypts it before persisting it; callers must still treat the bytes as sensitive.
    suspend fun uploadBackup(
        accessToken: String,
        backup: BackupUploadRequest,
    ): Result<BackupUploadResponse> = Result.failure(UnsupportedOperationException("Backup uploads are not supported by this cloud client"))

    suspend fun listBackups(accessToken: String): Result<BackupListResponse> =
        Result.failure(UnsupportedOperationException("Backup listing is not supported by this cloud client"))

    suspend fun getBackup(
        accessToken: String,
        backupId: String,
    ): Result<BackupInfoResponse> = Result.failure(UnsupportedOperationException("Backup metadata is not supported by this cloud client"))

    suspend fun downloadBackup(
        accessToken: String,
        backupId: String,
    ): Result<BackupDownloadResponse> =
        Result.failure(UnsupportedOperationException("Backup downloads are not supported by this cloud client"))

    suspend fun deleteBackup(
        accessToken: String,
        backupId: String,
    ): Result<Unit> = Result.failure(UnsupportedOperationException("Backup deletion is not supported by this cloud client"))
}

/**
 * Response indicating whether a username is available.
 */
@kotlinx.serialization.Serializable
data class CheckUsernameAvailabilityResponse(
    val available: Boolean,
    val username: String,
)

/**
 * Exception thrown for API errors.
 *
 * @property retryAfterMillis How long the server asked for before the next attempt, when it said;
 *   a retry is never scheduled sooner than this.
 */
class CloudApiException(
    val errorCode: String,
    override val message: String,
    val statusCode: Int? = null,
    cause: Throwable? = null,
    val retryAfterMillis: Long? = null,
) : Exception(message, cause)
