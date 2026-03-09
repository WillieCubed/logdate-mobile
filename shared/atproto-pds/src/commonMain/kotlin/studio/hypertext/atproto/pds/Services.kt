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
    public fun exchangeAuthorizationCode(request: AuthorizationCodeTokenRequest): Result<OAuthTokenResponse>

    /**
     * Exchanges a refresh token for a new token pair.
     */
    public fun exchangeRefreshToken(request: RefreshTokenGrantRequest): Result<OAuthTokenResponse>

    /**
     * Revokes a refresh token.
     */
    public fun revokeRefreshToken(request: OAuthRevokeRequest): Result<Unit>

    /**
     * Returns the current DPoP nonce value.
     */
    public fun nonce(): String
}
