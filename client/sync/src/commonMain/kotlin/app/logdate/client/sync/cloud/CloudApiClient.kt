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

    /**
     * Uploads media files to the cloud.
     */
    suspend fun uploadMedia(
        accessToken: String,
        media: MediaUploadRequest,
    ): Result<MediaUploadResponse>

    /**
     * Downloads media file by ID.
     */
    suspend fun downloadMedia(
        accessToken: String,
        mediaId: String,
    ): Result<MediaDownloadResponse>
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
 */
class CloudApiException(
    val errorCode: String,
    override val message: String,
    val statusCode: Int? = null,
    cause: Throwable? = null,
) : Exception(message, cause)
