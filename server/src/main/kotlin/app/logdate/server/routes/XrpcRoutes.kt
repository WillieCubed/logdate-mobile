package app.logdate.server.routes

import app.logdate.server.atproto.InvalidSwapException
import app.logdate.server.auth.Account
import app.logdate.server.auth.AccountRepository
import app.logdate.server.auth.TokenService
import app.logdate.server.identity.AtprotoIdentityService
import app.logdate.server.oauth.OAuthAccessTokenService
import app.logdate.server.oauth.OAuthDpopVerifier
import app.logdate.server.oauth.OAuthNonceService
import app.logdate.server.oauth.OAuthUseDpopNonceException
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.plugins.origin
import io.ktor.server.request.contentType
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.pds.CreateRecordRequest
import studio.hypertext.atproto.pds.DeleteRecordRequest
import studio.hypertext.atproto.pds.EmptyPdsResponse
import studio.hypertext.atproto.pds.GetBlobRequest
import studio.hypertext.atproto.pds.GetRecordRequest
import studio.hypertext.atproto.pds.ListRecordsRequest
import studio.hypertext.atproto.pds.PdsBlobService
import studio.hypertext.atproto.pds.PdsDiscoveryService
import studio.hypertext.atproto.pds.PdsErrorResponse
import studio.hypertext.atproto.pds.PdsRepoService
import studio.hypertext.atproto.pds.PutRecordRequest
import studio.hypertext.atproto.pds.UploadBlobRequest
import studio.hypertext.atproto.repo.Cid
import studio.hypertext.atproto.repo.RepoRecordId
import studio.hypertext.atproto.repo.UnsupportedCollectionException
import studio.hypertext.atproto.syntax.Nsid
import studio.hypertext.atproto.syntax.RecordKey
import java.util.UUID

fun Route.xrpcRoutes(
    identityService: AtprotoIdentityService,
    discoveryService: PdsDiscoveryService? = null,
    accountRepository: AccountRepository? = null,
    tokenService: TokenService? = null,
    repoService: PdsRepoService? = null,
    blobService: PdsBlobService? = null,
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
                    PdsErrorResponse("InvalidRequest", "Handle is required"),
                )
                return@get
            }

            val result = identityService.resolveHandle(handleValue)
            val resolved = result.getOrNull()
            if (resolved == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    PdsErrorResponse("HandleNotFound", "Handle not found: ${handleValue.lowercase()}"),
                )
                return@get
            }

            call.respond(HttpStatusCode.OK, resolved)
        }

        get("/com.atproto.server.describeServer") {
            val response =
                discoveryService?.describeServer()
                    ?: studio.hypertext.atproto.pds.DescribeServerResponse(
                        did = identityService.config.serverDid,
                        availableUserDomains = listOf(identityService.config.normalizedHandleDomain),
                        inviteCodeRequired = false,
                        phoneVerificationRequired = false,
                    )
            call.respond(HttpStatusCode.OK, response)
        }

        get("/com.atproto.repo.describeRepo") {
            val repoValue =
                call.request.queryParameters["repo"]
                    ?.trim()
                    .orEmpty()
            if (repoValue.isBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    PdsErrorResponse("InvalidRequest", "Repo is required"),
                )
                return@get
            }

            val result = identityService.describeRepo(repoValue)
            val repoDescription = result.getOrNull()
            if (repoDescription == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    PdsErrorResponse("RepoNotFound", "Repo not found: $repoValue"),
                )
                return@get
            }

            call.respond(HttpStatusCode.OK, repoDescription)
        }

        get("/com.atproto.repo.getRecord") {
            val repoApi =
                repoService ?: return@get call.respond(
                    HttpStatusCode.NotImplemented,
                    PdsErrorResponse("Unsupported", "Repo service is not configured"),
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
                    PdsErrorResponse("InvalidRequest", "repo, collection, and rkey are required"),
                )
                return@get
            }

            val recordId =
                parseRecordId(identityService, repoValue, collectionValue, recordKeyValue)
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        PdsErrorResponse("InvalidRequest", "Invalid repo, collection, or record key"),
                    )

            val recordResult =
                repoApi.getRecord(
                    GetRecordRequest(
                        repo = recordId.repo,
                        collection = recordId.collection,
                        recordKey = recordId.recordKey,
                        cid =
                            call.request.queryParameters["cid"]
                                ?.trim()
                                ?.takeIf(String::isNotEmpty),
                    ),
                )
            if (recordResult.isFailure) {
                call.respondForRepoError(recordResult.exceptionOrNull())
                return@get
            }
            val record = recordResult.getOrNull()
            if (record == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    PdsErrorResponse("RecordNotFound", "Record not found"),
                )
                return@get
            }

            call.respond(HttpStatusCode.OK, record)
        }

        get("/com.atproto.repo.listRecords") {
            val repoApi =
                repoService ?: return@get call.respond(
                    HttpStatusCode.NotImplemented,
                    PdsErrorResponse("Unsupported", "Repo service is not configured"),
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
                    PdsErrorResponse("InvalidRequest", "repo and collection are required"),
                )
                return@get
            }

            val repo = resolveRepoDid(identityService, repoValue)
            val collection = Nsid.parse(collectionValue).getOrNull()
            if (repo == null || collection == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    PdsErrorResponse("InvalidRequest", "Invalid repo or collection"),
                )
                return@get
            }

            val result =
                repoApi.listRecords(
                    ListRecordsRequest(
                        repo = repo,
                        collection = collection,
                        limit =
                            (
                                call.request.queryParameters["limit"]?.toIntOrNull()
                                    ?: studio.hypertext.atproto.pds.ListRecordsRequest.DEFAULT_PAGE_SIZE
                            ).coerceIn(1, 100),
                        cursor =
                            call.request.queryParameters["cursor"]
                                ?.trim()
                                ?.takeIf(String::isNotEmpty),
                        reverse = call.request.queryParameters["reverse"]?.toBooleanStrictOrNull() ?: false,
                    ),
                )
            val page = result.getOrNull()
            if (page != null) {
                call.respond(HttpStatusCode.OK, page)
                return@get
            }

            call.respondForRepoError(result.exceptionOrNull())
        }

        post("/com.atproto.repo.createRecord") {
            val repoApi =
                repoService ?: return@post call.respond(
                    HttpStatusCode.NotImplemented,
                    PdsErrorResponse("Unsupported", "Repo service is not configured"),
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
            val wireRequest = call.receive<CreateRecordInput>()
            val repo =
                resolveRepoDid(identityService, wireRequest.repo)
                    ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        PdsErrorResponse("InvalidRequest", "Invalid repo"),
                    )
            if (!ownsRepo(authenticatedAccount, wireRequest.repo)) {
                call.respond(HttpStatusCode.Forbidden, PdsErrorResponse("RepoMismatch", "Authenticated account does not own repo"))
                return@post
            }
            val request =
                CreateRecordRequest(
                    repo = repo,
                    collection = wireRequest.collection,
                    record = wireRequest.record,
                    recordKey = wireRequest.recordKey,
                    validate = wireRequest.validate,
                    swapCommit = wireRequest.swapCommit,
                )

            val result =
                repoApi.createRecord(request)
            val writeResult = result.getOrNull()
            if (writeResult != null) {
                call.respond(HttpStatusCode.OK, writeResult)
                return@post
            }

            call.respondForRepoError(result.exceptionOrNull())
        }

        post("/com.atproto.repo.putRecord") {
            val repoApi =
                repoService ?: return@post call.respond(
                    HttpStatusCode.NotImplemented,
                    PdsErrorResponse("Unsupported", "Repo service is not configured"),
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
            val wireRequest = call.receive<PutRecordInput>()
            val repo =
                resolveRepoDid(identityService, wireRequest.repo)
                    ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        PdsErrorResponse("InvalidRequest", "Invalid repo"),
                    )
            if (!ownsRepo(authenticatedAccount, wireRequest.repo)) {
                call.respond(HttpStatusCode.Forbidden, PdsErrorResponse("RepoMismatch", "Authenticated account does not own repo"))
                return@post
            }
            val request =
                PutRecordRequest(
                    repo = repo,
                    collection = wireRequest.collection,
                    recordKey = wireRequest.recordKey,
                    record = wireRequest.record,
                    validate = wireRequest.validate,
                    swapRecord = wireRequest.swapRecord,
                    swapCommit = wireRequest.swapCommit,
                )

            val result = repoApi.putRecord(request)
            val writeResult = result.getOrNull()
            if (writeResult != null) {
                call.respond(HttpStatusCode.OK, writeResult)
                return@post
            }

            call.respondForRepoError(result.exceptionOrNull())
        }

        post("/com.atproto.repo.deleteRecord") {
            val repoApi =
                repoService ?: return@post call.respond(
                    HttpStatusCode.NotImplemented,
                    PdsErrorResponse("Unsupported", "Repo service is not configured"),
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
            val wireRequest = call.receive<DeleteRecordInput>()
            val repo =
                resolveRepoDid(identityService, wireRequest.repo)
                    ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        PdsErrorResponse("InvalidRequest", "Invalid repo"),
                    )
            if (!ownsRepo(authenticatedAccount, wireRequest.repo)) {
                call.respond(HttpStatusCode.Forbidden, PdsErrorResponse("RepoMismatch", "Authenticated account does not own repo"))
                return@post
            }
            val request =
                DeleteRecordRequest(
                    repo = repo,
                    collection = wireRequest.collection,
                    recordKey = wireRequest.recordKey,
                    swapRecord = wireRequest.swapRecord,
                    swapCommit = wireRequest.swapCommit,
                )

            val result = repoApi.deleteRecord(request)
            if (result.isSuccess) {
                call.respond(HttpStatusCode.OK, EmptyPdsResponse())
                return@post
            }

            call.respondForRepoError(result.exceptionOrNull())
        }

        post("/com.atproto.repo.uploadBlob") {
            val pdsBlobService =
                blobService ?: return@post call.respond(
                    HttpStatusCode.NotImplemented,
                    PdsErrorResponse("Unsupported", "Blob service is not configured"),
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
            val repoDid = authenticatedAccount.did?.let(AtprotoDid::parse)?.getOrNull()
            if (repoDid == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    PdsErrorResponse("InvalidRequest", "Authenticated account does not have a valid DID"),
                )
                return@post
            }
            val body = call.receive<ByteArray>()
            val contentType =
                call.request
                    .header(HttpHeaders.ContentType)
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?: call.request
                        .contentType()
                        .toString()
                        .takeIf(String::isNotBlank)
                    ?: ContentType.Application.OctetStream
                        .toString()
            val result =
                pdsBlobService.uploadBlob(
                    UploadBlobRequest(
                        repo = repoDid,
                        contentType = contentType,
                        bytes = body,
                    ),
                )
            val uploaded = result.getOrNull()
            if (uploaded != null) {
                call.respond(HttpStatusCode.OK, uploaded)
                return@post
            }

            call.respondForBlobError(result.exceptionOrNull())
        }

        get("/com.atproto.sync.getBlob") {
            val pdsBlobService =
                blobService ?: return@get call.respond(
                    HttpStatusCode.NotImplemented,
                    PdsErrorResponse("Unsupported", "Blob service is not configured"),
                )
            val didValue =
                call.request.queryParameters["did"]
                    ?.trim()
                    .orEmpty()
            val cidValue =
                call.request.queryParameters["cid"]
                    ?.trim()
                    .orEmpty()
            val did = AtprotoDid.parse(didValue).getOrNull()
            val cid = Cid.parse(cidValue).getOrNull()
            if (did == null || cid == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    PdsErrorResponse("InvalidRequest", "did and cid are required"),
                )
                return@get
            }

            val result =
                pdsBlobService.getBlob(
                    GetBlobRequest(
                        did = did,
                        cid = cid,
                    ),
                )
            val blob = result.getOrNull()
            if (blob != null) {
                val contentType =
                    runCatching {
                        ContentType.parse(blob.contentType)
                    }.getOrDefault(ContentType.Application.OctetStream)
                call.respondBytes(
                    bytes = blob.bytes,
                    contentType = contentType,
                    status = HttpStatusCode.OK,
                )
                return@get
            }
            if (result.isSuccess) {
                call.respond(
                    HttpStatusCode.NotFound,
                    PdsErrorResponse("BlobNotFound", "Blob not found"),
                )
                return@get
            }

            call.respondForBlobError(result.exceptionOrNull())
        }
    }
}

@Serializable
private data class CreateRecordInput(
    val repo: String,
    val collection: Nsid,
    val record: JsonObject,
    @SerialName("rkey")
    val recordKey: RecordKey? = null,
    val validate: Boolean? = null,
    val swapCommit: String? = null,
)

@Serializable
private data class PutRecordInput(
    val repo: String,
    val collection: Nsid,
    @SerialName("rkey")
    val recordKey: RecordKey,
    val record: JsonObject,
    val validate: Boolean? = null,
    val swapRecord: String? = null,
    val swapCommit: String? = null,
)

@Serializable
private data class DeleteRecordInput(
    val repo: String,
    val collection: Nsid,
    @SerialName("rkey")
    val recordKey: RecordKey,
    val swapRecord: String? = null,
    val swapCommit: String? = null,
)

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
            PdsErrorResponse("Unsupported", "Repo auth is not configured"),
        )
        return null
    }

    val authHeader = request.header(HttpHeaders.Authorization)
    if (authHeader == null) {
        respond(HttpStatusCode.Unauthorized, PdsErrorResponse("AuthRequired", "Missing bearer token"))
        return null
    }

    if (authHeader.startsWith("DPoP ")) {
        if (oauthAccessTokenService == null || oauthDpopVerifier == null || oauthNonceService == null) {
            respond(HttpStatusCode.NotImplemented, PdsErrorResponse("Unsupported", "OAuth DPoP auth is not configured"))
            return null
        }
        val accessToken = authHeader.removePrefix("DPoP ").trim()
        val accessClaims = oauthAccessTokenService.validateAccessToken(accessToken)
        if (accessClaims == null) {
            respond(HttpStatusCode.Unauthorized, PdsErrorResponse("AuthRequired", "Invalid DPoP access token"))
            return null
        }
        val proof =
            request
                .header(DPOP_HEADER)
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: run {
                    respond(HttpStatusCode.Unauthorized, PdsErrorResponse("AuthRequired", "Missing DPoP proof"))
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
                respond(HttpStatusCode.Unauthorized, PdsErrorResponse("AuthRequired", error.message ?: "Invalid DPoP proof"))
                return null
            }
        if (verified.keyThumbprint != accessClaims.keyThumbprint) {
            respond(HttpStatusCode.Unauthorized, PdsErrorResponse("AuthRequired", "DPoP proof does not match the access token"))
            return null
        }
        val account =
            accountRepository.findByDid(accessClaims.subjectDid)?.let { identityService.ensureIdentity(it) }
        if (account == null) {
            respond(HttpStatusCode.Unauthorized, PdsErrorResponse("AuthRequired", "Account not found for DPoP access token"))
            return null
        }
        return account
    }

    if (!authHeader.startsWith("Bearer ")) {
        respond(HttpStatusCode.Unauthorized, PdsErrorResponse("AuthRequired", "Missing bearer token"))
        return null
    }

    val subject = tokenService.validateAccessToken(authHeader.removePrefix("Bearer ").trim())
    val accountId = subject?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    if (accountId == null) {
        respond(HttpStatusCode.Unauthorized, PdsErrorResponse("AuthRequired", "Invalid bearer token"))
        return null
    }
    val account =
        accountRepository
            .findById(kotlin.uuid.Uuid.parse(accountId.toString()))
            ?.let { identityService.ensureIdentity(it) }
    if (account == null) {
        respond(HttpStatusCode.Unauthorized, PdsErrorResponse("AuthRequired", "Account not found for bearer token"))
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
): AtprotoDid? =
    resolveRepoAccount(identityService, repo)
        ?.did
        ?.let(AtprotoDid::require)

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
                PdsErrorResponse("InvalidRequest", error.message ?: "Unsupported collection"),
            )

        is studio.hypertext.atproto.repo.InvalidRepoCursorException ->
            respond(
                HttpStatusCode.BadRequest,
                PdsErrorResponse("InvalidRequest", error.message ?: "Invalid cursor"),
            )

        is InvalidSwapException ->
            respond(
                HttpStatusCode.BadRequest,
                PdsErrorResponse("InvalidSwap", error.message ?: "swapRecord did not match"),
            )

        else ->
            respond(
                HttpStatusCode.BadRequest,
                PdsErrorResponse("InvalidRequest", error?.message ?: "Invalid XRPC request"),
            )
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.respondForBlobError(error: Throwable?) {
    respond(
        HttpStatusCode.BadRequest,
        PdsErrorResponse("InvalidRequest", error?.message ?: "Invalid blob request"),
    )
}
