package app.logdate.server.routes

import app.logdate.server.auth.Account
import app.logdate.server.auth.AccountRepository
import app.logdate.server.auth.TokenService
import app.logdate.server.identity.AtprotoIdentityService
import app.logdate.server.identity.IdentityLifecycleConflictException
import app.logdate.server.identity.IdentityLifecycleValidationException
import app.logdate.server.identity.SigningKeyService
import app.logdate.server.identity.didKeyFor
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

@Serializable
data class RotateSigningKeyRequest(
    val passphrase: String,
)

@Serializable
data class RotateSigningKeyData(
    val did: String,
    val handle: String,
    val previousPublicKeyDidKey: String,
    val exportedKey: SigningKeyService.ExportedSigningKey,
)

@Serializable
data class RotateSigningKeyResponse(
    val success: Boolean,
    val data: RotateSigningKeyData,
)

@Serializable
data class ImportSigningKeyRequest(
    val passphrase: String,
    val exportedKey: SigningKeyService.ExportedSigningKey,
)

@Serializable
data class ImportSigningKeyData(
    val did: String,
    val handle: String,
    val publicKeyDidKey: String,
)

@Serializable
data class ImportSigningKeyResponse(
    val success: Boolean,
    val data: ImportSigningKeyData,
)

@Serializable
data class RegisterPlcRecoveryKeyRequest(
    val recoveryDidKey: String,
)

@Serializable
data class RegisterPlcRecoveryKeyData(
    val did: String,
    val handle: String,
    val recoveryDidKey: String,
)

@Serializable
data class RegisterPlcRecoveryKeyResponse(
    val success: Boolean,
    val data: RegisterPlcRecoveryKeyData,
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
            } catch (error: IdentityLifecycleValidationException) {
                call.respondIdentityApiError(
                    HttpStatusCode.BadRequest,
                    "SIGNING_KEY_EXPORT_INVALID",
                    error.message ?: "Invalid signing key export request",
                )
            } catch (error: IdentityLifecycleConflictException) {
                call.respondIdentityApiError(
                    HttpStatusCode.Conflict,
                    "SIGNING_KEY_EXPORT_CONFLICT",
                    error.message ?: "Signing key export conflict",
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

        post("/signing-key/rotate") {
            val account =
                resolveIdentityApiAccount(
                    call = call,
                    accountRepository = accountRepository,
                    tokenService = tokenService,
                ) ?: return@post

            try {
                val request = call.receive<RotateSigningKeyRequest>()
                if (request.passphrase.isBlank()) {
                    call.respondIdentityApiError(HttpStatusCode.BadRequest, "PASSPHRASE_REQUIRED", "Passphrase is required")
                    return@post
                }

                val rotated = atprotoIdentityService.rotateSigningKey(account)
                val updatedAccount = rotated.account
                val exportedKey = signingKeyService.exportActiveKey(updatedAccount.id, request.passphrase)

                call.respond(
                    HttpStatusCode.OK,
                    RotateSigningKeyResponse(
                        success = true,
                        data =
                            RotateSigningKeyData(
                                did = requireNotNull(updatedAccount.did),
                                handle = requireNotNull(updatedAccount.handle),
                                previousPublicKeyDidKey = didKeyFor(rotated.previousPublicKeyMultibase),
                                exportedKey = exportedKey,
                            ),
                    ),
                )
            } catch (error: IdentityLifecycleValidationException) {
                call.respondIdentityApiError(
                    HttpStatusCode.BadRequest,
                    "SIGNING_KEY_ROTATION_INVALID",
                    error.message ?: "Invalid signing key rotation request",
                )
            } catch (error: IdentityLifecycleConflictException) {
                call.respondIdentityApiError(
                    HttpStatusCode.Conflict,
                    "SIGNING_KEY_ROTATION_CONFLICT",
                    error.message ?: "Signing key rotation conflict",
                )
            } catch (error: Exception) {
                Napier.e("Failed to rotate AT Protocol signing key", error)
                call.respondIdentityApiError(
                    HttpStatusCode.InternalServerError,
                    "SIGNING_KEY_ROTATION_FAILED",
                    "Failed to rotate signing key",
                )
            }
        }

        post("/signing-key/import") {
            val account =
                resolveIdentityApiAccount(
                    call = call,
                    accountRepository = accountRepository,
                    tokenService = tokenService,
                ) ?: return@post

            try {
                val request = call.receive<ImportSigningKeyRequest>()
                if (request.passphrase.isBlank()) {
                    call.respondIdentityApiError(HttpStatusCode.BadRequest, "PASSPHRASE_REQUIRED", "Passphrase is required")
                    return@post
                }

                val imported =
                    atprotoIdentityService.importSigningKey(
                        account = account,
                        exportedKey = request.exportedKey,
                        passphrase = request.passphrase,
                    )
                val updatedAccount = imported.account

                call.respond(
                    HttpStatusCode.OK,
                    ImportSigningKeyResponse(
                        success = true,
                        data =
                            ImportSigningKeyData(
                                did = requireNotNull(updatedAccount.did),
                                handle = requireNotNull(updatedAccount.handle),
                                publicKeyDidKey = didKeyFor(imported.activeKey.publicKeyMultibase),
                            ),
                    ),
                )
            } catch (error: IdentityLifecycleValidationException) {
                call.respondIdentityApiError(
                    HttpStatusCode.BadRequest,
                    "SIGNING_KEY_IMPORT_INVALID",
                    error.message ?: "Invalid signing key import payload",
                )
            } catch (error: IdentityLifecycleConflictException) {
                call.respondIdentityApiError(
                    HttpStatusCode.Conflict,
                    "SIGNING_KEY_IMPORT_CONFLICT",
                    error.message ?: "Signing key import conflict",
                )
            } catch (error: Exception) {
                Napier.e("Failed to import AT Protocol signing key", error)
                call.respondIdentityApiError(
                    HttpStatusCode.InternalServerError,
                    "SIGNING_KEY_IMPORT_FAILED",
                    "Failed to import signing key",
                )
            }
        }

        post("/plc/recovery-key") {
            val account =
                resolveIdentityApiAccount(
                    call = call,
                    accountRepository = accountRepository,
                    tokenService = tokenService,
                ) ?: return@post

            try {
                val request = call.receive<RegisterPlcRecoveryKeyRequest>()
                if (request.recoveryDidKey.isBlank()) {
                    call.respondIdentityApiError(
                        HttpStatusCode.BadRequest,
                        "RECOVERY_DID_KEY_REQUIRED",
                        "Recovery did:key is required",
                    )
                    return@post
                }

                val registered =
                    atprotoIdentityService.registerPlcRecoveryKey(
                        account = account,
                        recoveryDidKey = request.recoveryDidKey,
                    )
                val updatedAccount = registered.account

                call.respond(
                    HttpStatusCode.OK,
                    RegisterPlcRecoveryKeyResponse(
                        success = true,
                        data =
                            RegisterPlcRecoveryKeyData(
                                did = requireNotNull(updatedAccount.did),
                                handle = requireNotNull(updatedAccount.handle),
                                recoveryDidKey = registered.recoveryDidKey,
                            ),
                    ),
                )
            } catch (error: IdentityLifecycleValidationException) {
                call.respondIdentityApiError(
                    HttpStatusCode.BadRequest,
                    "PLC_RECOVERY_KEY_INVALID",
                    error.message ?: "Invalid PLC recovery key",
                )
            } catch (error: IdentityLifecycleConflictException) {
                call.respondIdentityApiError(
                    HttpStatusCode.Conflict,
                    "PLC_RECOVERY_KEY_CONFLICT",
                    error.message ?: "PLC recovery key conflict",
                )
            } catch (error: Exception) {
                Napier.e("Failed to register hosted PLC recovery key", error)
                call.respondIdentityApiError(
                    HttpStatusCode.InternalServerError,
                    "PLC_RECOVERY_KEY_FAILED",
                    "Failed to register PLC recovery key",
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
