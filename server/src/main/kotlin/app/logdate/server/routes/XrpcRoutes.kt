package app.logdate.server.routes

import app.logdate.server.atproto.AtprotoContentRecordStore
import app.logdate.server.atproto.InvalidSwapException
import app.logdate.server.auth.Account
import app.logdate.server.auth.AccountRepository
import app.logdate.server.auth.TokenService
import app.logdate.server.identity.AtprotoIdentityService
import app.logdate.server.oauth.OAuthAccessTokenService
import app.logdate.server.oauth.OAuthDpopVerifier
import app.logdate.server.oauth.OAuthNonceService
import app.logdate.server.oauth.OAuthUseDpopNonceException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.plugins.origin
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import studio.hypertext.atproto.repo.RepoRecord
import studio.hypertext.atproto.repo.RepoRecordId
import studio.hypertext.atproto.repo.RepoRecordStore
import studio.hypertext.atproto.repo.UnsupportedCollectionException
import studio.hypertext.atproto.syntax.Nsid
import studio.hypertext.atproto.syntax.RecordKey
import java.util.UUID

@Serializable
private data class XrpcErrorResponse(
    val error: String,
    val message: String,
)

@Serializable
private data class ResolveHandleResponse(
    val did: String,
)

@Serializable
private data class DescribeServerResponse(
    val did: String,
    val availableUserDomains: List<String>,
    val inviteCodeRequired: Boolean,
    val phoneVerificationRequired: Boolean,
)

@Serializable
private data class DescribeRepoResponse(
    val handle: String,
    val did: String,
    val didDoc: studio.hypertext.atproto.identity.DidDocument,
    val collections: List<String>,
    val handleIsCorrect: Boolean,
)

@Serializable
private data class ListRecordsResponse(
    val records: List<RepoRecord>,
    val cursor: String? = null,
)

@Serializable
private data class CreateRecordRequest(
    val repo: String,
    val collection: String,
    val rkey: String? = null,
    val validate: Boolean? = null,
    val record: JsonObject,
    val swapCommit: String? = null,
)

@Serializable
private data class PutRecordRequest(
    val repo: String,
    val collection: String,
    val rkey: String,
    val validate: Boolean? = null,
    val record: JsonObject,
    val swapRecord: String? = null,
    val swapCommit: String? = null,
)

@Serializable
private data class DeleteRecordRequest(
    val repo: String,
    val collection: String,
    val rkey: String,
    val swapRecord: String? = null,
    val swapCommit: String? = null,
)

@Serializable
private class EmptyXrpcResponse

fun Route.xrpcRoutes(
    identityService: AtprotoIdentityService,
    accountRepository: AccountRepository? = null,
    tokenService: TokenService? = null,
    repoRecordStore: RepoRecordStore? = null,
    oauthAccessTokenService: OAuthAccessTokenService? = null,
    oauthDpopVerifier: OAuthDpopVerifier? = null,
    oauthNonceService: OAuthNonceService? = null,
) {
    route("/xrpc") {
        get("/com.atproto.identity.resolveHandle") {
            val handleValue =
                call.request.queryParameters["handle"]
                    ?.trim()
                    .orEmpty()
            if (handleValue.isBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    XrpcErrorResponse("InvalidRequest", "Handle is required"),
                )
                return@get
            }

            val account = runCatching { identityService.findByHandle(handleValue) }.getOrNull()
            if (account?.did == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    XrpcErrorResponse("HandleNotFound", "Handle not found: ${handleValue.lowercase()}"),
                )
                return@get
            }

            call.respond(HttpStatusCode.OK, ResolveHandleResponse(account.did))
        }

        get("/com.atproto.server.describeServer") {
            call.respond(
                HttpStatusCode.OK,
                DescribeServerResponse(
                    did = identityService.config.serverDid,
                    availableUserDomains = listOf(identityService.config.normalizedHandleDomain),
                    inviteCodeRequired = false,
                    phoneVerificationRequired = false,
                ),
            )
        }

        get("/com.atproto.repo.describeRepo") {
            val repoValue =
                call.request.queryParameters["repo"]
                    ?.trim()
                    .orEmpty()
            if (repoValue.isBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    XrpcErrorResponse("InvalidRequest", "Repo is required"),
                )
                return@get
            }

            val account = resolveRepoAccount(identityService, repoValue)
            if (account?.did == null || account.handle == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    XrpcErrorResponse("RepoNotFound", "Repo not found: $repoValue"),
                )
                return@get
            }

            val collections =
                when (repoRecordStore) {
                    is AtprotoContentRecordStore -> repoRecordStore.collectionsForDid(account.did).map(Nsid::toString)
                    else -> emptyList()
                }
            call.respond(
                HttpStatusCode.OK,
                DescribeRepoResponse(
                    handle = account.handle,
                    did = account.did,
                    didDoc = identityService.documentFor(account),
                    collections = collections,
                    handleIsCorrect = true,
                ),
            )
        }

        get("/com.atproto.repo.getRecord") {
            val repoStore =
                repoRecordStore ?: return@get call.respond(
                    HttpStatusCode.NotImplemented,
                    XrpcErrorResponse("Unsupported", "Repo record store is not configured"),
                )
            val repoValue =
                call.request.queryParameters["repo"]
                    ?.trim()
                    .orEmpty()
            val collectionValue =
                call.request.queryParameters["collection"]
                    ?.trim()
                    .orEmpty()
            val recordKeyValue =
                call.request.queryParameters["rkey"]
                    ?.trim()
                    .orEmpty()
            if (repoValue.isBlank() || collectionValue.isBlank() || recordKeyValue.isBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    XrpcErrorResponse("InvalidRequest", "repo, collection, and rkey are required"),
                )
                return@get
            }

            val recordId =
                parseRecordId(identityService, repoValue, collectionValue, recordKeyValue)
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        XrpcErrorResponse("InvalidRequest", "Invalid repo, collection, or record key"),
                    )

            val recordResult = repoStore.getRecord(recordId)
            if (recordResult.isFailure) {
                call.respondForRepoError(recordResult.exceptionOrNull())
                return@get
            }
            val record = recordResult.getOrNull()
            if (record == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    XrpcErrorResponse("RecordNotFound", "Record not found"),
                )
                return@get
            }

            val cidConstraint =
                call.request.queryParameters["cid"]
                    ?.trim()
                    .orEmpty()
            if (cidConstraint.isNotBlank() && record.cid != cidConstraint) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    XrpcErrorResponse("RecordNotFound", "Record not found"),
                )
                return@get
            }

            call.respond(HttpStatusCode.OK, record)
        }

        get("/com.atproto.repo.listRecords") {
            val repoStore =
                repoRecordStore ?: return@get call.respond(
                    HttpStatusCode.NotImplemented,
                    XrpcErrorResponse("Unsupported", "Repo record store is not configured"),
                )
            val repoValue =
                call.request.queryParameters["repo"]
                    ?.trim()
                    .orEmpty()
            val collectionValue =
                call.request.queryParameters["collection"]
                    ?.trim()
                    .orEmpty()
            if (repoValue.isBlank() || collectionValue.isBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    XrpcErrorResponse("InvalidRequest", "repo and collection are required"),
                )
                return@get
            }

            val repo = resolveRepoDid(identityService, repoValue)
            val collection = Nsid.parse(collectionValue).getOrNull()
            if (repo == null || collection == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    XrpcErrorResponse("InvalidRequest", "Invalid repo or collection"),
                )
                return@get
            }

            val result =
                repoStore.listRecords(
                    repo = repo,
                    collection = collection,
                    limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: RepoRecordStore.DEFAULT_PAGE_SIZE).coerceIn(1, 100),
                    cursor =
                        call.request.queryParameters["cursor"]
                            ?.trim()
                            ?.takeIf(String::isNotEmpty),
                    reverse = call.request.queryParameters["reverse"]?.toBooleanStrictOrNull() ?: false,
                )
            val page = result.getOrNull()
            if (page != null) {
                call.respond(HttpStatusCode.OK, ListRecordsResponse(records = page.records, cursor = page.cursor))
                return@get
            }

            call.respondForRepoError(result.exceptionOrNull())
        }

        post("/com.atproto.repo.createRecord") {
            val repoStore =
                repoRecordStore ?: return@post call.respond(
                    HttpStatusCode.NotImplemented,
                    XrpcErrorResponse("Unsupported", "Repo record store is not configured"),
                )
            val authenticatedAccount =
                call.requireAuthenticatedAccount(
                    identityService = identityService,
                    accountRepository = accountRepository,
                    tokenService = tokenService,
                    oauthAccessTokenService = oauthAccessTokenService,
                    oauthDpopVerifier = oauthDpopVerifier,
                    oauthNonceService = oauthNonceService,
                ) ?: return@post
            val request = call.receive<CreateRecordRequest>()
            val repo = resolveRepoDid(identityService, request.repo)
            val collection = Nsid.parse(request.collection).getOrNull()
            val recordKey = request.rkey?.let { RecordKey.parse(it).getOrNull() }
            if (repo == null || collection == null || (request.rkey != null && recordKey == null)) {
                call.respond(HttpStatusCode.BadRequest, XrpcErrorResponse("InvalidRequest", "Invalid repo, collection, or record key"))
                return@post
            }
            if (!ownsRepo(authenticatedAccount, request.repo)) {
                call.respond(HttpStatusCode.Forbidden, XrpcErrorResponse("RepoMismatch", "Authenticated account does not own repo"))
                return@post
            }

            val result = repoStore.createRecord(repo = repo, collection = collection, value = request.record, recordKey = recordKey)
            val writeResult = result.getOrNull()
            if (writeResult != null) {
                call.respond(HttpStatusCode.OK, writeResult)
                return@post
            }

            call.respondForRepoError(result.exceptionOrNull())
        }

        post("/com.atproto.repo.putRecord") {
            val repoStore =
                repoRecordStore ?: return@post call.respond(
                    HttpStatusCode.NotImplemented,
                    XrpcErrorResponse("Unsupported", "Repo record store is not configured"),
                )
            val authenticatedAccount =
                call.requireAuthenticatedAccount(
                    identityService = identityService,
                    accountRepository = accountRepository,
                    tokenService = tokenService,
                    oauthAccessTokenService = oauthAccessTokenService,
                    oauthDpopVerifier = oauthDpopVerifier,
                    oauthNonceService = oauthNonceService,
                ) ?: return@post
            val request = call.receive<PutRecordRequest>()
            if (!ownsRepo(authenticatedAccount, request.repo)) {
                call.respond(HttpStatusCode.Forbidden, XrpcErrorResponse("RepoMismatch", "Authenticated account does not own repo"))
                return@post
            }

            val recordId =
                parseRecordId(identityService, request.repo, request.collection, request.rkey)
                    ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        XrpcErrorResponse("InvalidRequest", "Invalid repo, collection, or record key"),
                    )
            val result = repoStore.putRecord(recordId = recordId, value = request.record, swapRecord = request.swapRecord)
            val writeResult = result.getOrNull()
            if (writeResult != null) {
                call.respond(HttpStatusCode.OK, writeResult)
                return@post
            }

            call.respondForRepoError(result.exceptionOrNull())
        }

        post("/com.atproto.repo.deleteRecord") {
            val repoStore =
                repoRecordStore ?: return@post call.respond(
                    HttpStatusCode.NotImplemented,
                    XrpcErrorResponse("Unsupported", "Repo record store is not configured"),
                )
            val authenticatedAccount =
                call.requireAuthenticatedAccount(
                    identityService = identityService,
                    accountRepository = accountRepository,
                    tokenService = tokenService,
                    oauthAccessTokenService = oauthAccessTokenService,
                    oauthDpopVerifier = oauthDpopVerifier,
                    oauthNonceService = oauthNonceService,
                ) ?: return@post
            val request = call.receive<DeleteRecordRequest>()
            if (!ownsRepo(authenticatedAccount, request.repo)) {
                call.respond(HttpStatusCode.Forbidden, XrpcErrorResponse("RepoMismatch", "Authenticated account does not own repo"))
                return@post
            }

            val recordId =
                parseRecordId(identityService, request.repo, request.collection, request.rkey)
                    ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        XrpcErrorResponse("InvalidRequest", "Invalid repo, collection, or record key"),
                    )
            val result = repoStore.deleteRecord(recordId = recordId, swapRecord = request.swapRecord)
            if (result.isSuccess) {
                call.respond(HttpStatusCode.OK, EmptyXrpcResponse())
                return@post
            }

            call.respondForRepoError(result.exceptionOrNull())
        }
    }
}

@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
private suspend fun io.ktor.server.application.ApplicationCall.requireAuthenticatedAccount(
    identityService: AtprotoIdentityService,
    accountRepository: AccountRepository?,
    tokenService: TokenService?,
    oauthAccessTokenService: OAuthAccessTokenService?,
    oauthDpopVerifier: OAuthDpopVerifier?,
    oauthNonceService: OAuthNonceService?,
): Account? {
    if (accountRepository == null || tokenService == null) {
        respond(
            HttpStatusCode.NotImplemented,
            XrpcErrorResponse("Unsupported", "Repo auth is not configured"),
        )
        return null
    }

    val authHeader = request.header(HttpHeaders.Authorization)
    if (authHeader == null) {
        respond(HttpStatusCode.Unauthorized, XrpcErrorResponse("AuthRequired", "Missing bearer token"))
        return null
    }

    if (authHeader.startsWith("DPoP ")) {
        if (oauthAccessTokenService == null || oauthDpopVerifier == null || oauthNonceService == null) {
            respond(HttpStatusCode.NotImplemented, XrpcErrorResponse("Unsupported", "OAuth DPoP auth is not configured"))
            return null
        }
        val accessToken = authHeader.removePrefix("DPoP ").trim()
        val accessClaims = oauthAccessTokenService.validateAccessToken(accessToken)
        if (accessClaims == null) {
            respond(HttpStatusCode.Unauthorized, XrpcErrorResponse("AuthRequired", "Invalid DPoP access token"))
            return null
        }
        val proof =
            request
                .header(DPOP_HEADER)
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: run {
                    respond(HttpStatusCode.Unauthorized, XrpcErrorResponse("AuthRequired", "Missing DPoP proof"))
                    return null
                }
        val verification =
            oauthDpopVerifier.verify(
                proof = proof,
                method = request.httpMethod.value,
                htu = absoluteRequestUrl(),
                expectedNonce = oauthNonceService.currentNonce(),
                expectedAth = oauthDpopVerifier.accessTokenHash(accessToken),
            )
        val verified =
            verification.getOrElse { error ->
                if (error is OAuthUseDpopNonceException) {
                    response.headers.append(DPOP_NONCE_HEADER, error.nonce)
                }
                respond(HttpStatusCode.Unauthorized, XrpcErrorResponse("AuthRequired", error.message ?: "Invalid DPoP proof"))
                return null
            }
        if (verified.keyThumbprint != accessClaims.keyThumbprint) {
            respond(HttpStatusCode.Unauthorized, XrpcErrorResponse("AuthRequired", "DPoP proof does not match the access token"))
            return null
        }
        val account =
            accountRepository.findByDid(accessClaims.subjectDid)?.let { identityService.ensureIdentity(it) }
        if (account == null) {
            respond(HttpStatusCode.Unauthorized, XrpcErrorResponse("AuthRequired", "Account not found for DPoP access token"))
            return null
        }
        return account
    }

    if (!authHeader.startsWith("Bearer ")) {
        respond(HttpStatusCode.Unauthorized, XrpcErrorResponse("AuthRequired", "Missing bearer token"))
        return null
    }

    val subject = tokenService.validateAccessToken(authHeader.removePrefix("Bearer ").trim())
    val accountId = subject?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    if (accountId == null) {
        respond(HttpStatusCode.Unauthorized, XrpcErrorResponse("AuthRequired", "Invalid bearer token"))
        return null
    }
    val account =
        accountRepository
            .findById(kotlin.uuid.Uuid.parse(accountId.toString()))
            ?.let { identityService.ensureIdentity(it) }
    if (account == null) {
        respond(HttpStatusCode.Unauthorized, XrpcErrorResponse("AuthRequired", "Account not found for bearer token"))
        return null
    }

    return account
}

private fun io.ktor.server.application.ApplicationCall.absoluteRequestUrl(): String {
    val origin = request.origin
    val host =
        if (origin.serverPort == DEFAULT_HTTPS_PORT || origin.serverPort == DEFAULT_HTTP_PORT) {
            origin.serverHost
        } else {
            "${origin.serverHost}:${origin.serverPort}"
        }
    return "${origin.scheme}://$host${request.path()}"
}

private const val DPOP_HEADER = "DPoP"
private const val DPOP_NONCE_HEADER = "DPoP-Nonce"
private const val DEFAULT_HTTPS_PORT = 443
private const val DEFAULT_HTTP_PORT = 80

private suspend fun resolveRepoAccount(
    identityService: AtprotoIdentityService,
    repo: String,
): Account? =
    identityService.findByDid(repo.trim())
        ?: identityService.findByHandle(repo.trim())

private suspend fun resolveRepoDid(
    identityService: AtprotoIdentityService,
    repo: String,
): studio.hypertext.atproto.identity.AtprotoDid? =
    resolveRepoAccount(identityService, repo)?.did?.let(studio.hypertext.atproto.identity.AtprotoDid::require)

private suspend fun parseRecordId(
    identityService: AtprotoIdentityService,
    repo: String,
    collection: String,
    recordKey: String,
): RepoRecordId? {
    val resolvedRepo = resolveRepoDid(identityService, repo) ?: return null
    val resolvedCollection = Nsid.parse(collection).getOrNull() ?: return null
    val resolvedRecordKey = RecordKey.parse(recordKey).getOrNull() ?: return null
    return RepoRecordId(repo = resolvedRepo, collection = resolvedCollection, recordKey = resolvedRecordKey)
}

private fun ownsRepo(
    account: Account,
    repo: String,
): Boolean {
    val normalized = repo.trim().lowercase()
    return normalized == account.did?.lowercase() || normalized == account.handle?.lowercase()
}

private suspend fun io.ktor.server.application.ApplicationCall.respondForRepoError(error: Throwable?) {
    when (error) {
        is UnsupportedCollectionException ->
            respond(
                HttpStatusCode.BadRequest,
                XrpcErrorResponse("InvalidRequest", error.message ?: "Unsupported collection"),
            )

        is studio.hypertext.atproto.repo.InvalidRepoCursorException ->
            respond(
                HttpStatusCode.BadRequest,
                XrpcErrorResponse("InvalidRequest", error.message ?: "Invalid cursor"),
            )

        is InvalidSwapException ->
            respond(
                HttpStatusCode.BadRequest,
                XrpcErrorResponse("InvalidSwap", error.message ?: "swapRecord did not match"),
            )

        else ->
            respond(
                HttpStatusCode.BadRequest,
                XrpcErrorResponse("InvalidRequest", error?.message ?: "Invalid XRPC request"),
            )
    }
}
