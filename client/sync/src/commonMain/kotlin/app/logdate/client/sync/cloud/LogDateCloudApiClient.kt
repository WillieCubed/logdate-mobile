package app.logdate.client.sync.cloud

import app.logdate.client.datastore.SessionStorage
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.ApiErrorResponse
import app.logdate.shared.model.BeginAccountCreationRequest
import app.logdate.shared.model.BeginAccountCreationResponse
import app.logdate.shared.model.CompleteAccountCreationRequest
import app.logdate.shared.model.CompleteAccountCreationResponse
import app.logdate.shared.model.LogDateAccount
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.io.files.Path
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Implementation of [CloudApiClient] for communicating with the LogDate Cloud API.
 *
 * This client uses Ktor for HTTP requests and handles authentication, serialization,
 * and error handling for all LogDate Cloud API interactions.
 *
 * @param configRepository The runtime server configuration for the selected LogDate server.
 * @param httpClient The Ktor HTTP client instance to use for requests. Defaults to the application's shared client.
 */
class LogDateCloudApiClient(
    private val configRepository: LogDateConfigRepository,
    private val httpClient: HttpClient,
    private val sessionStorage: SessionStorage? = null,
    diagnostics: (app.logdate.shared.model.diagnostics.SyncDiagnosticEvent) -> Unit = {},
    diagnosticSource: suspend () -> app.logdate.client.sync.diagnostics.DiagnosticSource? = { null },
    scopedDiagnostics: (
        app.logdate.shared.model.diagnostics.SyncDiagnosticEvent,
        app.logdate.client.sync.diagnostics.DiagnosticSource?,
    ) -> Unit = { _, _ -> },
) : CloudApiClient {
    internal val transport = SafeCloudTransport(httpClient, diagnostics, diagnosticSource, scopedDiagnostics)
    private val errorJson = Json { ignoreUnknownKeys = true }

    internal suspend fun getBaseUrl(
        accessToken: String? = null,
        refreshToken: String? = null,
    ): String {
        val pinned = currentCoroutineContext()[CloudRequestBinding]
        if (pinned != null) {
            if ((accessToken != null && accessToken != pinned.authorizedAccessToken) ||
                (refreshToken != null && refreshToken != pinned.session.refreshToken)
            ) {
                throw scopeChanged()
            }
            return pinned.location.apiBaseUrl
        }

        val origin = configRepository.getCurrentBackendUrl()
        val selectedBaseUrl = configRepository.getCurrentApiBaseUrl()
        val bound = if (accessToken != null || refreshToken != null) sessionStorage?.getOriginBoundSession() else null
        if (sessionStorage != null &&
            (accessToken != null || refreshToken != null) &&
            (
                bound?.origin != origin ||
                    (accessToken != null && bound.session.accessToken != accessToken) ||
                    (refreshToken != null && bound.session.refreshToken != refreshToken)
            )
        ) {
            throw scopeChanged()
        }
        val baseUrl = configRepository.apiBaseUrl.first()
        if (baseUrl != selectedBaseUrl ||
            configRepository.getCurrentBackendUrl() != origin ||
            (bound != null && sessionStorage?.getOriginBoundSession() != bound)
        ) {
            throw scopeChanged()
        }
        return baseUrl
    }

    private fun scopeChanged() =
        CloudApiException(
            "CLOUD_SCOPE_CHANGED",
            "Cloud account changed during request",
            statusCode = 401,
        )

    override suspend fun checkUsernameAvailability(username: String): Result<CheckUsernameAvailabilityResponse> =
        requestCheckUsernameAvailability(username)

    override suspend fun beginAccountCreation(request: BeginAccountCreationRequest): Result<BeginAccountCreationResponse> =
        requestBeginAccountCreation(request)

    override suspend fun completeAccountCreation(request: CompleteAccountCreationRequest): Result<CompleteAccountCreationResponse> =
        requestCompleteAccountCreation(request)

    override suspend fun refreshAccessToken(refreshToken: String): Result<String> = requestRefreshAccessToken(refreshToken)

    override suspend fun getAccountInfo(accessToken: String): Result<LogDateAccount> = requestGetAccountInfo(accessToken)

    override suspend fun uploadContent(
        accessToken: String,
        content: ContentUploadRequest,
    ): Result<ContentUploadResponse> = requestUploadContent(accessToken, content)

    override suspend fun getContentChanges(
        accessToken: String,
        since: Long,
        limit: Int?,
    ): Result<ContentChangesResponse> = requestGetContentChanges(accessToken, since, limit)

    override suspend fun updateContent(
        accessToken: String,
        contentId: String,
        content: ContentUpdateRequest,
    ): Result<ContentUpdateResponse> = requestUpdateContent(accessToken, contentId, content)

    override suspend fun deleteContent(
        accessToken: String,
        contentId: String,
    ): Result<Unit> = requestDeleteContent(accessToken, contentId)

    override suspend fun uploadJournal(
        accessToken: String,
        journal: JournalUploadRequest,
    ): Result<JournalUploadResponse> = requestUploadJournal(accessToken, journal)

    override suspend fun getJournalChanges(
        accessToken: String,
        since: Long,
        limit: Int?,
    ): Result<JournalChangesResponse> = requestGetJournalChanges(accessToken, since, limit)

    override suspend fun updateJournal(
        accessToken: String,
        journalId: String,
        journal: JournalUpdateRequest,
    ): Result<JournalUpdateResponse> = requestUpdateJournal(accessToken, journalId, journal)

    override suspend fun deleteJournal(
        accessToken: String,
        journalId: String,
    ): Result<Unit> = requestDeleteJournal(accessToken, journalId)

    override suspend fun uploadAssociations(
        accessToken: String,
        associations: AssociationUploadRequest,
    ): Result<AssociationUploadResponse> = requestUploadAssociations(accessToken, associations)

    override suspend fun getAssociationChanges(
        accessToken: String,
        since: Long,
        limit: Int?,
    ): Result<AssociationChangesResponse> = requestGetAssociationChanges(accessToken, since, limit)

    override suspend fun deleteAssociations(
        accessToken: String,
        associations: AssociationDeleteRequest,
    ): Result<Unit> = requestDeleteAssociations(accessToken, associations)

    override suspend fun uploadDraft(
        accessToken: String,
        draft: DraftUploadRequest,
    ): Result<DraftUploadResponse> = requestUploadDraft(accessToken, draft)

    override suspend fun getDraftChanges(
        accessToken: String,
        since: Long,
        limit: Int?,
    ): Result<DraftChangesResponse> = requestGetDraftChanges(accessToken, since, limit)

    override suspend fun deleteDraft(
        accessToken: String,
        draftId: String,
    ): Result<Unit> = requestDeleteDraft(accessToken, draftId)

    override suspend fun uploadMedia(
        accessToken: String,
        media: MediaUpload,
    ): Result<MediaUploadResponse> = requestUploadMedia(accessToken, media)

    override suspend fun downloadMedia(
        accessToken: String,
        mediaId: String,
    ): Result<MediaDownloadResponse> = requestDownloadMedia(accessToken, mediaId)

    override suspend fun uploadBackupFile(
        accessToken: String,
        backup: BackupUploadFileRequest,
    ): Result<BackupUploadResponse> = requestUploadBackupFile(accessToken, backup)

    override suspend fun downloadBackupToFile(
        accessToken: String,
        backupId: String,
        destination: Path,
    ): Result<BackupInfoResponse> = requestDownloadBackupToFile(accessToken, backupId, destination)

    override suspend fun uploadBackup(
        accessToken: String,
        backup: BackupUploadRequest,
    ): Result<BackupUploadResponse> = requestUploadBackup(accessToken, backup)

    override suspend fun listBackups(accessToken: String): Result<BackupListResponse> = requestListBackups(accessToken)

    override suspend fun getBackup(
        accessToken: String,
        backupId: String,
    ): Result<BackupInfoResponse> = requestGetBackup(accessToken, backupId)

    override suspend fun downloadBackup(
        accessToken: String,
        backupId: String,
    ): Result<BackupDownloadResponse> = requestDownloadBackup(accessToken, backupId)

    override suspend fun deleteBackup(
        accessToken: String,
        backupId: String,
    ): Result<Unit> = requestDeleteBackup(accessToken, backupId)

    internal suspend fun <T> handleApiError(response: HttpResponse): Result<T> {
        val statusCode = response.status.value
        val errorPayload =
            runCatching { response.bodyAsText() }
                .onFailure { Napier.w("Could not read error body for HTTP") }
                .getOrDefault("")
        // Response bodies are untrusted and can echo journal content or credentials.
        Napier.w("Sync API request failed: HTTP")
        val parsedError = parseErrorPayload(errorPayload)
        return if (parsedError != null) {
            Result.failure(
                CloudApiException(
                    errorCode = safeCloudErrorCode(parsedError.code),
                    message = "Request failed (HTTP $statusCode)",
                    statusCode = statusCode,
                ),
            )
        } else {
            Result.failure(
                CloudApiException(
                    errorCode = "UNKNOWN_ERROR",
                    message = "Request failed (HTTP $statusCode)",
                    statusCode = statusCode,
                ),
            )
        }
    }

    private fun parseErrorPayload(payload: String): ParsedErrorBody? {
        if (payload.isBlank()) {
            return null
        }
        val authErrorBody = runCatching { errorJson.decodeFromString<ApiErrorResponse>(payload) }.getOrNull()
        if (authErrorBody != null) {
            return ParsedErrorBody(
                code = authErrorBody.error.code,
                message = authErrorBody.error.message,
            )
        }
        val syncErrorBody = runCatching { errorJson.decodeFromString<SyncErrorResponseBody>(payload) }.getOrNull()
        if (syncErrorBody != null && syncErrorBody.code.isNotBlank()) {
            return ParsedErrorBody(
                code = syncErrorBody.code,
                message = syncErrorBody.message,
            )
        }
        return null
    }
}

@Serializable
private data class SyncErrorResponseBody(
    val code: String,
    val message: String,
)

private data class ParsedErrorBody(
    val code: String,
    val message: String,
)
