package studio.hypertext.atproto.pds

import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.identity.DidDocument
import studio.hypertext.atproto.repo.RepoRecord
import studio.hypertext.atproto.repo.RepoWriteResult

/**
 * Discovery-facing PDS service contract.
 */
public interface PdsDiscoveryService {
    /**
     * Returns OAuth authorization-server metadata.
     */
    public fun authorizationServerMetadata(): AuthorizationServerMetadata

    /**
     * Returns OAuth protected-resource metadata.
     */
    public fun protectedResourceMetadata(): ProtectedResourceMetadata

    /**
     * Returns `com.atproto.server.describeServer` data.
     */
    public fun describeServer(): DescribeServerResponse
}

/**
 * Identity-facing PDS service contract.
 */
public interface PdsIdentityService {
    /**
     * Resolves [handle] to its current DID, or `null` when not found.
     */
    public suspend fun resolveHandle(handle: String): Result<ResolveHandleResponse?>

    /**
     * Describes [repo], or returns `null` when not found.
     */
    public suspend fun describeRepo(repo: String): Result<DescribeRepoResponse?>

    /**
     * Returns the DID document for [did], or `null` when not found.
     */
    public suspend fun didDocument(did: AtprotoDid): Result<DidDocument?>
}

/**
 * Account and session-facing PDS service contract.
 */
public interface PdsSessionService {
    /**
     * Creates a new hosted account and returns an authenticated session.
     */
    public suspend fun createAccount(request: CreateAccountRequest): Result<SessionResponse>

    /**
     * Creates a standard AT Protocol session for an existing account.
     */
    public suspend fun createSession(request: CreateSessionRequest): Result<SessionResponse>

    /**
     * Returns the authenticated account session identified by [accessJwt].
     */
    public suspend fun getSession(accessJwt: String): Result<SessionInfoResponse>

    /**
     * Refreshes the session identified by [refreshJwt].
     */
    public suspend fun refreshSession(refreshJwt: String): Result<SessionResponse>

    /**
     * Deletes the session identified by [refreshJwt].
     */
    public suspend fun deleteSession(refreshJwt: String): Result<Unit>
}

/**
 * Repo-facing PDS service contract.
 */
public interface PdsRepoService {
    /**
     * Returns an exact record request result.
     */
    public suspend fun getRecord(request: GetRecordRequest): Result<RepoRecord?>

    /**
     * Lists records matching [request].
     */
    public suspend fun listRecords(request: ListRecordsRequest): Result<ListRecordsResponse>

    /**
     * Creates a new record.
     */
    public suspend fun createRecord(request: CreateRecordRequest): Result<RepoWriteResult>

    /**
     * Replaces or inserts a record.
     */
    public suspend fun putRecord(request: PutRecordRequest): Result<RepoWriteResult>

    /**
     * Deletes a record.
     */
    public suspend fun deleteRecord(request: DeleteRecordRequest): Result<Boolean>
}

/**
 * Sync/export-facing PDS service contract.
 */
public interface PdsSyncService {
    /**
     * Exports a repository as a CAR payload.
     */
    public suspend fun getRepo(request: GetRepoRequest): Result<RepoExportResponse?>

    /**
     * Returns the latest commit metadata for [request].
     */
    public suspend fun getLatestCommit(request: GetLatestCommitRequest): Result<GetLatestCommitResponse?>

    /**
     * Returns the hosting status for [request].
     */
    public suspend fun getRepoStatus(request: GetRepoStatusRequest): Result<GetRepoStatusResponse?>
}

/**
 * Blob-facing PDS service contract.
 */
public interface PdsBlobService {
    /**
     * Uploads a new blob for later repo record references.
     */
    public suspend fun uploadBlob(request: UploadBlobRequest): Result<UploadBlobResponse>

    /**
     * Returns a raw blob download for [request], or `null` when not found.
     */
    public suspend fun getBlob(request: GetBlobRequest): Result<BlobDownload?>
}

/**
 * OAuth-facing PDS service contract.
 */
public interface PdsOAuthService {
    /**
     * Creates a pushed authorization request.
     */
    public suspend fun createPushedAuthorizationRequest(request: PushedAuthorizationRequest): Result<PushedAuthorizationResponse>

    /**
     * Loads the consent prompt for [requestUri].
     */
    public fun loadAuthorizationPrompt(requestUri: String): Result<AuthorizationPrompt>

    /**
     * Completes an authorization decision.
     */
    public fun completeAuthorization(request: AuthorizationDecisionRequest): Result<String>

    /**
     * Exchanges an authorization code for tokens.
     */
    public suspend fun exchangeAuthorizationCode(request: AuthorizationCodeTokenRequest): Result<OAuthTokenResponse>

    /**
     * Exchanges a refresh token for a new token pair.
     */
    public suspend fun exchangeRefreshToken(request: RefreshTokenGrantRequest): Result<OAuthTokenResponse>

    /**
     * Revokes a refresh token.
     */
    public suspend fun revokeRefreshToken(request: OAuthRevokeRequest): Result<Unit>

    /**
     * Returns the current DPoP nonce value.
     */
    public fun nonce(): String
}
