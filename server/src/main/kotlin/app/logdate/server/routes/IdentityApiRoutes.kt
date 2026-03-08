package app.logdate.server.routes

import app.logdate.server.auth.Account
import app.logdate.server.auth.AccountRepository
import app.logdate.server.auth.TokenService
import app.logdate.server.identity.AtprotoIdentityService
import app.logdate.server.identity.SigningKeyService
import app.logdate.shared.model.ApiError
import app.logdate.shared.model.ApiErrorResponse
import io.github.aakira.napier.Napier
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
data class ExportSigningKeyRequest(
    val passphrase: String,
)

@Serializable
data class ExportSigningKeyData(
    val did: String,
    val handle: String,
    val exportedKey: SigningKeyService.ExportedSigningKey,
)

@Serializable
data class ExportSigningKeyResponse(
    val success: Boolean,
    val data: ExportSigningKeyData,
)

@OptIn(ExperimentalUuidApi::class)
fun Route.identityApiRoutes(
    accountRepository: AccountRepository,
    tokenService: TokenService,
    atprotoIdentityService: AtprotoIdentityService,
    signingKeyService: SigningKeyService,
) {
    route("/identity") {
        post("/signing-key/export") {
            val account =
                resolveIdentityApiAccount(
                    call = call,
                    accountRepository = accountRepository,
                    tokenService = tokenService,
                ) ?: return@post

            try {
                val request = call.receive<ExportSigningKeyRequest>()
                if (request.passphrase.isBlank()) {
                    call.respondIdentityApiError(HttpStatusCode.BadRequest, "PASSPHRASE_REQUIRED", "Passphrase is required")
                    return@post
                }

                val ensuredAccount = atprotoIdentityService.ensureIdentity(account)
                val exportedKey = signingKeyService.exportActiveKey(ensuredAccount.id, request.passphrase)

                call.respond(
                    HttpStatusCode.OK,
                    ExportSigningKeyResponse(
                        success = true,
                        data =
                            ExportSigningKeyData(
                                did = requireNotNull(ensuredAccount.did),
                                handle = requireNotNull(ensuredAccount.handle),
                                exportedKey = exportedKey,
                            ),
                    ),
                )
            } catch (error: Exception) {
                Napier.e("Failed to export AT Protocol signing key", error)
                call.respondIdentityApiError(
                    HttpStatusCode.InternalServerError,
                    "SIGNING_KEY_EXPORT_FAILED",
                    "Failed to export signing key",
                )
            }
        }
    }
}

@OptIn(ExperimentalUuidApi::class)
private suspend fun resolveIdentityApiAccount(
    call: ApplicationCall,
    accountRepository: AccountRepository,
    tokenService: TokenService,
): Account? {
    val authHeader = call.request.header(HttpHeaders.Authorization)
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
        call.respondIdentityApiError(HttpStatusCode.Unauthorized, "INVALID_TOKEN", "Access token is required")
        return null
    }

    val token = authHeader.removePrefix("Bearer ").trim()
    if (token.isBlank()) {
        call.respondIdentityApiError(HttpStatusCode.Unauthorized, "INVALID_TOKEN", "Access token is required")
        return null
    }

    val accountId = tokenService.validateAccessToken(token)
    if (accountId == null) {
        call.respondIdentityApiError(HttpStatusCode.Unauthorized, "INVALID_TOKEN", "Invalid or expired access token")
        return null
    }

    val parsedAccountId =
        runCatching { Uuid.parse(accountId) }.getOrNull()
            ?: run {
                call.respondIdentityApiError(HttpStatusCode.Unauthorized, "INVALID_TOKEN", "Invalid or expired access token")
                return null
            }

    val account = accountRepository.findById(parsedAccountId)
    if (account == null) {
        call.respondIdentityApiError(HttpStatusCode.NotFound, "ACCOUNT_NOT_FOUND", "Account not found")
        return null
    }

    return account
}

private suspend fun ApplicationCall.respondIdentityApiError(
    status: HttpStatusCode,
    code: String,
    message: String,
) {
    respond(status, ApiErrorResponse(ApiError(code, message)))
}
